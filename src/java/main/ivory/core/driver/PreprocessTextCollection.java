/*
 * Ivory: A Hadoop toolkit for Web-scale information retrieval
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package ivory.core.driver;

import ivory.core.RetrievalEnvironment;
import ivory.core.preprocess.BuildDictionary;
import ivory.core.preprocess.BuildIntDocVectors;
import ivory.core.preprocess.BuildIntDocVectorsForwardIndex;
import ivory.core.preprocess.BuildTermDocVectors;
import ivory.core.preprocess.BuildTermDocVectorsForwardIndex;
import ivory.core.preprocess.BuildWeightedIntDocVectors;
import ivory.core.preprocess.BuildWeightedTermDocVectors;
import ivory.core.preprocess.ComputeGlobalTermStatistics;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import edu.umd.cloud9.collection.line.NumberTextDocuments;

public class PreprocessTextCollection extends Configured implements Tool {
  private static final Logger sLogger = Logger.getLogger(PreprocessTextCollection.class);

  private static int printUsage() {
    System.out.println("usage: [input-path] [index-path] [num-of-mappers] [num-of-reducers]");
    ToolRunner.printGenericCommandUsage(System.out);
    return -1;
  }

  /**
   * Runs this tool.
   */
  public int run(String[] args) throws Exception {
    if (args.length != 4) {
      printUsage();
      return -1;
    }

    String collection = args[0];
    String indexRootPath = args[1];
    int numMappers = Integer.parseInt(args[2]);
    int numReducers = Integer.parseInt(args[3]);

    sLogger.info("Tool name: PreprocessText");
    sLogger.info(" - Collection path: " + collection);
    sLogger.info(" - Index path: " + indexRootPath);

    Configuration conf = getConf();
    FileSystem fs = FileSystem.get(conf);

    // Create the index directory if it doesn't already exist.
    Path p = new Path(indexRootPath);
    if (!fs.exists(p)) {
      sLogger.info("index directory doesn't exist, creating...");
      fs.mkdirs(p);
    }

    RetrievalEnvironment env = new RetrievalEnvironment(indexRootPath, fs);

    // Look for the docno mapping, which maps from docid (String) to docno
    // (sequentially-number integer). If it doesn't exist create it.
    Path mappingFile = env.getDocnoMappingData();
    Path mappingDir = env.getDocnoMappingDirectory();

    if (!fs.exists(p)) {
      sLogger.info("docno-mapping.dat doesn't exist, creating...");
      String[] arr = new String[] { collection, mappingDir.toString(), mappingFile.toString(),
          new Integer(numMappers).toString() };
      NumberTextDocuments tool = new NumberTextDocuments();
      tool.setConf(conf);
      tool.run(arr);

      fs.delete(mappingDir, true);
    }

    // Now we're ready to start the preprocessing pipeline... set
    // appropriate properties.
    conf.setInt("Ivory.NumMapTasks", numMappers);
    conf.setInt("Ivory.NumReduceTasks", numReducers);

    conf.set("Ivory.CollectionName", "TextCollection");
    conf.set("Ivory.CollectionPath", collection);
    conf.set("Ivory.IndexPath", indexRootPath);
    conf.set("Ivory.InputFormat", "edu.umd.cloud9.collection.line.TextDocumentInputFormat");
    conf.set("Ivory.Tokenizer", "ivory.tokenize.GalagoTokenizer");
    conf.set("Ivory.DocnoMappingClass", "edu.umd.cloud9.collection.line.TextDocnoMapping");
    conf.set("Ivory.DocnoMappingFile", env.getDocnoMappingData().toString());
    conf.set("Ivory.DocnoMappingFile", "");

    conf.setInt("Ivory.DocnoOffset", 0); // docnos start at 1
    conf.setInt("Ivory.MinDf", 2); // toss away singleton terms
    conf.setInt("Ivory.MaxDf", Integer.MAX_VALUE);
    conf.setInt("Ivory.TermIndexWindow", 8);

    new BuildTermDocVectors(conf).run();
    new ComputeGlobalTermStatistics(conf).run();
    new BuildDictionary(conf).run();
    new BuildIntDocVectors(conf).run();

    new BuildIntDocVectorsForwardIndex(conf).run();
    new BuildTermDocVectorsForwardIndex(conf).run();

    conf.setBoolean("Ivory.Normalize", false);
    conf.set("Ivory.ScoringModel", "ivory.pwsim.score.Bm25");

    new BuildWeightedIntDocVectors(conf).run();
    new BuildWeightedTermDocVectors(conf).run();

    return 0;
  }

  /**
   * Dispatches command-line arguments to the tool via the
   * <code>ToolRunner</code>.
   */
  public static void main(String[] args) throws Exception {
    int res = ToolRunner.run(new Configuration(), new PreprocessTextCollection(), args);
    System.exit(res);
  }
}
