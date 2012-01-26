package ivory.sqe.querygenerator;

import ivory.core.tokenize.Tokenizer;
import ivory.core.tokenize.TokenizerFactory;
import ivory.sqe.retrieval.Constants;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mortbay.log.Log;

import edu.umd.cloud9.io.pair.PairOfFloatInt;
import edu.umd.cloud9.io.pair.PairOfStrings;
import edu.umd.cloud9.util.map.HMapKF;
import edu.umd.hooka.VocabularyWritable;
import edu.umd.hooka.alignment.HadoopAlign;
import edu.umd.hooka.ttables.TTable_monolithic_IFAs;

public class CLWordQueryGenerator implements QueryGenerator {
  private static final Logger LOG = Logger.getLogger(CLWordQueryGenerator.class);
  private Tokenizer tokenizer;
  private VocabularyWritable fVocab_f2e, eVocab_f2e;
  private TTable_monolithic_IFAs f2eProbs;
  private int length;
  private float lexProbThreshold = 0.01f, cumProbThreshold = 0.9f;
  private Set<PairOfStrings> pairsInSCFG;
  private boolean H6;
  
  public CLWordQueryGenerator() throws IOException {
    super();
  }

  public void init(FileSystem fs, Configuration conf) throws IOException {
    if(conf.get(Constants.fVocabPath)==null || conf.get(Constants.eVocabPath)==null || conf.get(Constants.Language)==null 
        || conf.get(Constants.TokenizerData)==null){
      throw new RuntimeException("Missing configuration parameters: "+conf.get(Constants.fVocabPath)+","
          +conf.get(Constants.eVocabPath)+","+conf.get(Constants.Language)+","+conf.get(Constants.TokenizerData));
    }
    fVocab_f2e = (VocabularyWritable) HadoopAlign.loadVocab(new Path(conf.get(Constants.fVocabPath)), fs);
    eVocab_f2e = (VocabularyWritable) HadoopAlign.loadVocab(new Path(conf.get(Constants.eVocabPath)), fs);

    f2eProbs = new TTable_monolithic_IFAs(fs, new Path(conf.get(Constants.f2eProbsPath)), true);
    tokenizer = TokenizerFactory.createTokenizer(fs, conf.get(Constants.Language), conf.get(Constants.TokenizerData), fVocab_f2e);
    lexProbThreshold = conf.getFloat(Constants.LexicalProbThreshold, lexProbThreshold);
    cumProbThreshold = conf.getFloat(Constants.CumulativeProbThreshold, cumProbThreshold);

    String h6 = conf.get(Constants.Heuristic6); 
    if (h6 == null || h6.equals("off")) {
      H6 = false;
    }else {
      H6 = true;
    }
    LOG.info("H6 = " + H6);
    
    if (H6 && conf.get(Constants.SCFGPath) != null) {
      pairsInSCFG = new HashSet<PairOfStrings>();
      getPairsInSCFG(conf.get(Constants.SCFGPath));
    }
  }

  private void getPairsInSCFG(String grammarFile) {
    BufferedReader reader;
    try {
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(grammarFile), "UTF-8"));
      String rule = null;
      while ((rule = reader.readLine())!=null) {
        //      LOG.info("SCFG rule = " + rule);
        String[] parts = rule.split("\\|\\|\\|");
        String[] lhs = parts[1].trim().split(" ");
        String[] rhs = parts[2].trim().split(" ");;
        for (String l : lhs) {
          for (String r : rhs) {
            pairsInSCFG.add(new PairOfStrings(l, r));
//            LOG.info("added "+l+"|||"+r);
          }
        }
      }
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public JSONObject parseQuery(String query) {
    JSONObject queryJson = new JSONObject();
    try {
      String[] tokens = tokenizer.processContent(query.trim());
      length = tokens.length;
      JSONArray tokenTranslations = new JSONArray();
      for (String token : tokens) {
        JSONObject tokenTrans = new JSONObject();
        JSONArray weights = getTranslations(token);
        if (weights != null) {				
          tokenTrans.put("#weight", weights);
          tokenTranslations.put(tokenTrans);
        }else {
          // ????
          //LOG.info("Skipped "+token);
        }
      }
      queryJson.put("#combine", tokenTranslations);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return queryJson;
  }

  protected JSONArray getTranslations(String token) {
    int f = fVocab_f2e.get(token);
    if (f <= 0) {
      //LOG.info("Skipping "+token);
      return null;
    }
    JSONArray arr = new JSONArray();
    PriorityQueue<PairOfFloatInt> eS = f2eProbs.get(f).getTranslationsWithProbs(lexProbThreshold);
//    LOG.info("Adding "+ eS.size() +" translations for "+token+","+f);

    float sumProbEF = 0;

    //tf(e) = sum_f{tf(f)*prob(e|f)}
    while (!eS.isEmpty()) {
      PairOfFloatInt entry = eS.poll();
      float probEF = entry.getLeftElement();
      int e = entry.getRightElement();
      String eTerm = eVocab_f2e.get(e);

      probEF = f2eProbs.get(f, e);
      if (probEF > 0 && e > 0 && (pairsInSCFG == null || pairsInSCFG.contains(new PairOfStrings(token,eTerm)))) {
        try {
          arr.put(probEF);
          arr.put(eTerm);
          sumProbEF += probEF;
//          LOG.info("adding "+eTerm+","+probEF+","+sumProbEF);
        } catch (JSONException e1) {
          throw new RuntimeException("Error adding translation and prob values");
        }
      }else {
//        LOG.info("skipping "+eTerm+","+probEF);				
      }

      // early terminate if cumulative prob. has reached specified threshold
      if (sumProbEF > cumProbThreshold) {
        break;
      }
    }

    // normalize weights
    for (int i = 0; i < arr.length(); i=i+2){
      try {
        float pr = (float) arr.getDouble(i);
        arr.put(i, pr/sumProbEF);
      } catch (JSONException e1) {
        throw new RuntimeException("Error normalizing");
      }
    }
    return arr;
  }

  public int getQueryLength(){
    return length;  
  }

}
