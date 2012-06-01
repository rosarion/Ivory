/*
 * Ivory: A Hadoop toolkit for web-scale information retrieval
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

package ivory.core.data.index;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import com.kamikaze.pfordelta.PForDelta;

import org.apache.hadoop.io.WritableUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Object representing a document-sorted postings list that holds positional information for terms.
 * Compression is done using PForDelta.
 *
 * @author Nima Asadi
 */
public class PostingsListDocSortedPositionalPForDelta implements PostingsList {
  private int[][] docidCompressed;
  private int[][] offsetCompressed;
  private int[][] tfCompressed;
  private int lastBlockSize;
  private int[][] positionsCompressed;
  private int positionsLastBlockSize;

  private transient PForDeltaUtility util;

  private int collectionDocumentCount = -1;
  private int numPostings = -1;
  private long sumOfPostingsScore;
  private int postingsAdded;
  private int df;
  private long cf;

  public PostingsListDocSortedPositionalPForDelta() {
    this.sumOfPostingsScore = 0;
    this.postingsAdded = 0;
    this.df = 0;
    this.cf = 0;
  }

  @Override
  public void clear() {
    docidCompressed = null;
    offsetCompressed = null;
    tfCompressed = null;
    lastBlockSize = 0;
    positionsCompressed = null;
    positionsLastBlockSize = 0;
    util = null;

    sumOfPostingsScore = 0;
    df = 0;
    cf = 0;
    numPostings = -1;
    postingsAdded = 0;
  }

  @Override
  public void add(int docno, short tf, TermPositions pos) {
    Preconditions.checkArgument(pos.getPositions().length != 0);
    Preconditions.checkArgument(tf == pos.getTf());

    if(util.add(docno, tf, pos)) {
      docidCompressed = util.getCompressedDocids();
      offsetCompressed = util.getCompressedOffsets();
      tfCompressed = util.getCompressedTfs();
      positionsCompressed = util.getCompressedPositions();
      lastBlockSize = util.getLastBlockSize();
      positionsLastBlockSize = util.getPositionsLastBlockSize();
    }
    sumOfPostingsScore += tf;
    postingsAdded++;
  }

  @Override
  public int size() {
    return postingsAdded;
  }

  @Override
  public PostingsReader getPostingsReader() {
    Preconditions.checkNotNull(docidCompressed);
    Preconditions.checkNotNull(tfCompressed);
    Preconditions.checkNotNull(offsetCompressed);
    Preconditions.checkNotNull(positionsCompressed);
    Preconditions.checkArgument(collectionDocumentCount > 0);
    Preconditions.checkArgument(postingsAdded > 0);

    return new PostingsReader(postingsAdded, collectionDocumentCount, this);
  }

  @Override
  public byte[] getRawBytes() {
    return null;
  }

  @Override
  public void setCollectionDocumentCount(int docs) {
    Preconditions.checkArgument(docs > 0);

    collectionDocumentCount = docs;
  }

  @Override
  public int getCollectionDocumentCount() {
    return collectionDocumentCount;
  }

  @Override
  public void setNumberOfPostings(int n) {
    numPostings = n;
    util = new PForDeltaUtility(n);
  }

  @Override
  public int getNumberOfPostings() {
    return numPostings;
  }

  @Override
  public int getDf() {
    return df;
  }

  @Override
  public void setDf(int df) {
    this.df = df;
  }

  @Override
  public long getCf() {
    return cf;
  }

  @Override
  public void setCf(long cf) {
    this.cf = cf;
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    postingsAdded = WritableUtils.readVInt(in);
    numPostings = postingsAdded;

    df = WritableUtils.readVInt(in);
    cf = WritableUtils.readVLong(in);
    sumOfPostingsScore = cf;

    lastBlockSize = in.readInt();
    docidCompressed = new int[in.readInt()][];
    tfCompressed = new int[docidCompressed.length][];
    offsetCompressed = new int[docidCompressed.length][];
    for(int i = 0; i < docidCompressed.length; i++) {
      docidCompressed[i] = new int[in.readInt()];
      tfCompressed[i] = new int[in.readInt()];
      offsetCompressed[i] = new int[in.readInt()];
      for(int j = 0; j < docidCompressed[i].length; j++) {
        docidCompressed[i][j] = in.readInt();
        tfCompressed[i][j] = in.readInt();
        offsetCompressed[i][j] = in.readInt();
      }
    }

    positionsLastBlockSize = in.readInt();
    positionsCompressed = new int[in.readInt()][];
    for(int i = 0; i < positionsCompressed.length; i++) {
      positionsCompressed[i] = new int[in.readInt()];
      for(int j = 0; j < positionsCompressed[i].length; j++) {
        positionsCompressed[i][j] = in.readInt();
      }
    }
  }

  @Override
  public void write(DataOutput out) throws IOException {
    WritableUtils.writeVInt(out, postingsAdded);
    WritableUtils.writeVInt(out, df == 0 ? postingsAdded : df);
    WritableUtils.writeVLong(out, cf == 0 ? sumOfPostingsScore : cf);

    out.writeInt(lastBlockSize);
    out.writeInt(docidCompressed.length);
    for(int i = 0; i < docidCompressed.length; i++) {
      out.writeInt(docidCompressed[i].length);
      out.writeInt(tfCompressed[i].length);
      out.writeInt(offsetCompressed[i].length);
      for(int j = 0; j < docidCompressed[i].length; j++) {
        out.writeInt(docidCompressed[i][j]);
        out.writeInt(tfCompressed[i][j]);
        out.writeInt(offsetCompressed[i][j]);
      }
    }

    out.writeInt(positionsLastBlockSize);
    out.writeInt(positionsCompressed.length);
    for(int i = 0; i < positionsCompressed.length; i++) {
      out.writeInt(positionsCompressed[i].length);
      for(int j = 0; j < positionsCompressed[i].length; j++) {
        out.writeInt(positionsCompressed[i][j]);
      }
    }
  }

  @Override public byte[] serialize() throws IOException {
    Preconditions.checkArgument(postingsAdded > 0);

    ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    DataOutputStream dataOut = new DataOutputStream(bytesOut);
    write(dataOut);

    return bytesOut.toByteArray();
  }

  public static PostingsListDocSortedPositionalPForDelta create(DataInput in) throws IOException {
    PostingsListDocSortedPositionalPForDelta p = new PostingsListDocSortedPositionalPForDelta();
    p.readFields(in);
    return p;
  }

  public static PostingsListDocSortedPositionalPForDelta create(byte[] bytes) throws IOException {
    return PostingsListDocSortedPositionalPForDelta.create(
        new DataInputStream(new ByteArrayInputStream(bytes)));
  }

  /**
   * {@code PostingsReader} for {@code PostingsListDocSortedPositionalPForDelta}.
   *
   * @author Nima Asadi
   */
  public static class PostingsReader implements ivory.core.data.index.PostingsReader {
    private int currentBlock = -1;
    private int currentOffsetBlock = -1;
    private int currentPositionBlock = -1;
    private int[] docidBlock = null;
    private int[] offsetBlock = null;
    private int[] tfBlock = null;
    private int[] positionBlock = null;

    private int cnt = 0;
    private int[] curPositions;
    private short innerPrevTf;
    private int innerPrevDocno;
    private int innerNumPostings;
    private int innerCollectionSize;
    private PostingsListDocSortedPositionalPForDelta postingsList;

    protected PostingsReader(int numPostings, int collectionSize,
        PostingsListDocSortedPositionalPForDelta list) {
      Preconditions.checkNotNull(list);
      Preconditions.checkArgument(numPostings > 0);
      Preconditions.checkArgument(collectionSize > 0);

      docidBlock = new int[PForDeltaUtility.BLOCK_SIZE];
      tfBlock = new int[PForDeltaUtility.BLOCK_SIZE];
      offsetBlock = new int[PForDeltaUtility.BLOCK_SIZE];
      positionBlock = new int[PForDeltaUtility.BLOCK_SIZE];

      innerNumPostings = numPostings;
      innerCollectionSize = collectionSize;
      postingsList = list;
    }

    @Override
    public int getNumberOfPostings() {
      return innerNumPostings;
    }

    @Override
    public void reset() {
      currentBlock = -1;
      currentOffsetBlock = -1;
      currentPositionBlock = -1;
      docidBlock = new int[PForDeltaUtility.BLOCK_SIZE];
      tfBlock = new int[PForDeltaUtility.BLOCK_SIZE];
      offsetBlock = new int[PForDeltaUtility.BLOCK_SIZE];
      positionBlock = new int[PForDeltaUtility.BLOCK_SIZE];
      cnt = 0;
    }

    @Override
    public boolean nextPosting(Posting p) {
      if(!hasMorePostings()) {
        return false;
      }

      int blockNumber = (int) ((double) cnt / (double) PForDeltaUtility.BLOCK_SIZE);
      int inBlockIndex = cnt % PForDeltaUtility.BLOCK_SIZE;

      if(blockNumber == postingsList.docidCompressed.length - 1 &&
         currentBlock != blockNumber) {
        docidBlock = new int[postingsList.lastBlockSize];
        tfBlock = new int[postingsList.lastBlockSize];
      }

      if(currentBlock != blockNumber) {
        PForDelta.decompressOneBlock(docidBlock, postingsList.docidCompressed[blockNumber], docidBlock.length);
        for(int i = 1; i < docidBlock.length; i++) {
          docidBlock[i] += docidBlock[i - 1];
        }
        PForDelta.decompressOneBlock(tfBlock, postingsList.tfCompressed[blockNumber], tfBlock.length);
      }

      p.setDocno(docidBlock[inBlockIndex]);
      p.setTf((short) tfBlock[inBlockIndex]);

      currentBlock = blockNumber;
      cnt++;
      innerPrevDocno = p.getDocno();
      innerPrevTf = p.getTf();
      curPositions = null;

      return true;
    }

    @Override
    public int[] getPositions() {
      if (curPositions != null) {
        return curPositions;
      }

      int cnt = this.cnt - 1;
      int blockNumber = (int) ((double) cnt / (double) PForDeltaUtility.BLOCK_SIZE);
      int inBlockIndex = cnt % PForDeltaUtility.BLOCK_SIZE;

      if(blockNumber == postingsList.offsetCompressed.length - 1 &&
         currentOffsetBlock != blockNumber) {
        offsetBlock = new int[postingsList.lastBlockSize];
      }

      if(currentOffsetBlock != blockNumber) {
        PForDelta.decompressOneBlock(offsetBlock, postingsList.offsetCompressed[blockNumber], offsetBlock.length);
        for(int i = 1; i < offsetBlock.length; i++) {
          offsetBlock[i] += offsetBlock[i - 1];
        }
      }

      int[] pos = new int[getTf()];

      int beginOffset = offsetBlock[inBlockIndex];
      int endOffset = beginOffset + pos.length - 1;
      int beginBlock = (int) ((double) beginOffset/(double) PForDeltaUtility.BLOCK_SIZE);

      if(beginBlock != currentPositionBlock &&
         beginBlock == postingsList.positionsCompressed.length - 1) {
        positionBlock = new int[postingsList.positionsLastBlockSize];
      }

      if(beginBlock != currentPositionBlock) {
        PForDelta.decompressOneBlock(positionBlock, postingsList.positionsCompressed[beginBlock], positionBlock.length);
      }

      int posIndex = 0;
      beginOffset %= PForDeltaUtility.BLOCK_SIZE;
      int endBlock = (int) ((double) endOffset / (double) PForDeltaUtility.BLOCK_SIZE);
      endOffset %= PForDeltaUtility.BLOCK_SIZE;

      if(endBlock != beginBlock) {
        pos[posIndex++] = positionBlock[beginOffset];
        for(int i = beginOffset + 1; i < positionBlock.length; i++) {
          pos[posIndex] = positionBlock[i] + pos[posIndex - 1];
          posIndex++;
        }

        if(endBlock == postingsList.positionsCompressed.length - 1) {
          positionBlock = new int[postingsList.positionsLastBlockSize];
        }

        PForDelta.decompressOneBlock(positionBlock, postingsList.positionsCompressed[endBlock], positionBlock.length);

        for(int i = 0; i <= endOffset; i++) {
          pos[posIndex] = positionBlock[i] + pos[posIndex - 1];
          posIndex++;
        }
      } else {
        pos[posIndex++] = positionBlock[beginOffset];
        for(int i = beginOffset + 1; i <= endOffset; i++) {
          pos[posIndex] = positionBlock[i] + pos[posIndex - 1];
          posIndex++;
        }
      }

      currentPositionBlock = endBlock;
      currentOffsetBlock = blockNumber;
      curPositions = pos;
      return pos;
    }

    @Override
    public boolean getPositions(TermPositions tp) {
      int[] pos = getPositions();

      if (pos == null) {
        return false;
      }

      tp.set(pos, (short) pos.length);
      return true;
    }

    @Override
    public boolean hasMorePostings() {
      return !(cnt >= innerNumPostings);
    }

    @Override
    public short peekNextTf() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int peekNextDocno() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PostingsList getPostingsList() {
      return postingsList;
    }

    @Override
    public int getDocno() {
      return innerPrevDocno;
    }

    @Override
    public short getTf() {
      return innerPrevTf;
    }
  }

  public static PostingsListDocSortedPositionalPForDelta merge(PostingsListDocSortedPositionalPForDelta plist1,
      PostingsListDocSortedPositionalPForDelta plist2, int docs) {
    Preconditions.checkNotNull(plist1);
    Preconditions.checkNotNull(plist2);

    plist1.setCollectionDocumentCount(docs);
    plist2.setCollectionDocumentCount(docs);

    int numPostings1 = plist1.getNumberOfPostings();
    int numPostings2 = plist2.getNumberOfPostings();

    PostingsListDocSortedPositionalPForDelta newPostings = new PostingsListDocSortedPositionalPForDelta();
    newPostings.setCollectionDocumentCount(docs);
    newPostings.setNumberOfPostings(numPostings1 + numPostings2);

    Posting posting1 = new Posting();
    PostingsReader reader1 = plist1.getPostingsReader();

    Posting posting2 = new Posting();
    PostingsReader reader2 = plist2.getPostingsReader();

    reader1.nextPosting(posting1);
    reader2.nextPosting(posting2);

    TermPositions tp1 = new TermPositions();
    TermPositions tp2 = new TermPositions();

    reader1.getPositions(tp1);
    reader2.getPositions(tp2);

    while (true) {
      if (posting1 == null) {
        newPostings.add(posting2.getDocno(), posting2.getTf(), tp2);

        // Read the rest from reader 2.
        while (reader2.nextPosting(posting2)) {
          reader2.getPositions(tp2);
          newPostings.add(posting2.getDocno(), posting2.getTf(), tp2);
        }

        break;
      } else if (posting2 == null) {
        newPostings.add(posting1.getDocno(), posting1.getTf(), tp1);

        // Read the rest from reader 1.
        while (reader1.nextPosting(posting1)) {
          reader1.getPositions(tp1);
          newPostings.add(posting1.getDocno(), posting1.getTf(), tp1);
        }

        break;

      } else if (posting1.getDocno() < posting2.getDocno()) {
        newPostings.add(posting1.getDocno(), posting1.getTf(), tp1);

        if (reader1.nextPosting(posting1) == false) {
          posting1 = null;
        } else {
          reader1.getPositions(tp1);
        }
      } else {
        newPostings.add(posting2.getDocno(), posting2.getTf(), tp2);

        if (reader2.nextPosting(posting2) == false) {
          posting2 = null;
        } else {
          reader2.getPositions(tp2);
        }
      }
    }

    return newPostings;
  }

  public static PostingsListDocSortedPositionalPForDelta merge(PostingsList plist1,
      PostingsList plist2, int docs) {
    Preconditions.checkNotNull(plist1);
    Preconditions.checkNotNull(plist2);

    plist1.setCollectionDocumentCount(docs);
    plist2.setCollectionDocumentCount(docs);

    int numPostings1 = plist1.getNumberOfPostings();
    int numPostings2 = plist2.getNumberOfPostings();

    PostingsListDocSortedPositionalPForDelta newPostings = new PostingsListDocSortedPositionalPForDelta();
    newPostings.setCollectionDocumentCount(docs);
    newPostings.setNumberOfPostings(numPostings1 + numPostings2);

    Posting posting1 = new Posting();
    ivory.core.data.index.PostingsReader reader1 = plist1.getPostingsReader();

    Posting posting2 = new Posting();
    ivory.core.data.index.PostingsReader reader2 = plist2.getPostingsReader();

    reader1.nextPosting(posting1);
    reader2.nextPosting(posting2);

    TermPositions tp1 = new TermPositions();
    TermPositions tp2 = new TermPositions();

    reader1.getPositions(tp1);
    reader2.getPositions(tp2);

    while (true) {
      if (posting1 == null) {
        newPostings.add(posting2.getDocno(), posting2.getTf(), tp2);

        // Read the rest from reader 2.
        while (reader2.nextPosting(posting2)) {
          reader2.getPositions(tp2);
          newPostings.add(posting2.getDocno(), posting2.getTf(), tp2);
        }

        break;
      } else if (posting2 == null) {
        newPostings.add(posting1.getDocno(), posting1.getTf(), tp1);

        // Read the rest from reader 1.
        while (reader1.nextPosting(posting1)) {
          reader1.getPositions(tp1);
          newPostings.add(posting1.getDocno(), posting1.getTf(), tp1);
        }

        break;
      } else if (posting1.getDocno() < posting2.getDocno()) {
        newPostings.add(posting1.getDocno(), posting1.getTf(), tp1);

        if (reader1.nextPosting(posting1) == false) {
          posting1 = null;
        } else {
          reader1.getPositions(tp1);
        }
      } else {
        newPostings.add(posting2.getDocno(), posting2.getTf(), tp2);

        if (reader2.nextPosting(posting2) == false) {
          posting2 = null;
        } else {
          reader2.getPositions(tp2);
        }
      }
    }

    return newPostings;
  }

  public static void mergeList(PostingsList newPostings, List<PostingsList> list, int nCollDocs) {
    Preconditions.checkNotNull(list);
    int nLists = list.size();

    // A reader for each postings list.
    ivory.core.data.index.PostingsReader[] reader = new PostingsReader[nLists];

    Posting[] posting = new Posting[nLists];        // The cur posting of each list.
    TermPositions[] tp = new TermPositions[nLists]; // The cur positions of each list.

    // min-heap for merging
    PriorityQueue<DocList> heap = new PriorityQueue<DocList>(nLists, comparator);

    int totalPostings = 0;
    int i = 0;
    for (PostingsList pl : list) {
      pl.setCollectionDocumentCount(nCollDocs);

      totalPostings += pl.getNumberOfPostings();

      reader[i] = pl.getPostingsReader();

      posting[i] = new Posting();
      reader[i].nextPosting(posting[i]);

      tp[i] = new TermPositions();
      reader[i].getPositions(tp[i]);
      heap.add(new DocList(posting[i].getDocno(), i));

      i++;
    }

    newPostings.setCollectionDocumentCount(nCollDocs);
    newPostings.setNumberOfPostings(totalPostings);

    DocList dl;
    while (heap.size() > 0) {
      dl = heap.remove();
      i = dl.listIndex;
      newPostings.add(dl.id, posting[i].getTf(), tp[i]);

      if (reader[i].nextPosting(posting[i])) {
        reader[i].getPositions(tp[i]);
        dl.set(posting[i].getDocno(), i);
        heap.add(dl);
      }
    }
  }

  private static class DocList {
    public int id;
    public int listIndex;

    public DocList(int id, int listIndex) {
      this.id = id;
      this.listIndex = listIndex;
    }

    public void set(int id, int listIndex) {
      this.id = id;
      this.listIndex = listIndex;
    }

    @Override
    public String toString() {
      return "{" + id + " - " + listIndex + "}";
    }
  }

  public static class DocListComparator implements Comparator<DocList> {
    public int compare(DocList t1, DocList t2) {
      if (t1.id < t2.id) {
        return -1;
      } else if (t1.id > t2.id) {
        return 1;
      }
      return 0;
    }
  }

  private static final DocListComparator comparator = new DocListComparator();

  private static class PForDeltaUtility {
    public static final int BLOCK_SIZE = 128;

    private int[][] docidCompressed;
    private int[][] offsetCompressed;
    private int[][] tfCompressed;
    private int lastBlockSize;
    private int[][] positionsCompressed;
    private int positionsLastBlockSize;

    private int[] docids = null;
    private int[] tfs = null;
    private int[] offsets = null;
    private List<Integer> positions;
    private int index;

    public PForDeltaUtility(int nbPostings) {
      docids = new int[nbPostings];
      tfs = new int[nbPostings];
      offsets = new int[nbPostings];
      positions = Lists.newArrayList();
      index = 0;
    }

    /**
     * Adds a posting.
     *
     * @param docid Document ID
     * @param tf Term frequency
     * @param pos Term positions
     * @return Whether or not all postings have been added
     */
    public boolean add(int docid, int tf, TermPositions pos) {
      Preconditions.checkNotNull(pos);

      if(index < docids.length) {
        this.docids[index] = docid;
        this.tfs[index] = tf;

        int[] posArray = pos.getPositions();
        this.offsets[index] = positions.size();
        if(posArray.length > 0) {
          positions.add(posArray[0]);
          for(int j = 1; j < posArray.length; j++) {
            positions.add(posArray[j] - posArray[j - 1]);
          }
        }
        index++;
      }

      if(index >= docids.length) {
        performCompression();
        return true;
      }
      return false;
    }

    private void performCompression() {
      docidCompressed = compressData(docids, BLOCK_SIZE, true);
      offsetCompressed = compressData(offsets, BLOCK_SIZE, true);
      tfCompressed = compressData(tfs, BLOCK_SIZE, false);
      lastBlockSize = computeLastBlockSize(docids.length, docidCompressed.length, BLOCK_SIZE);

      int[] posArray  = new int[positions.size()];
      for(int i = 0; i < positions.size(); i++) {
        posArray[i] = positions.get(i);
      }
      positionsCompressed = compressData(posArray, BLOCK_SIZE, false);
      positionsLastBlockSize = computeLastBlockSize(posArray.length, positionsCompressed.length, BLOCK_SIZE);
    }

    public int[][] getCompressedDocids() {
      return docidCompressed;
    }

    public int[][] getCompressedOffsets() {
      return offsetCompressed;
    }

    public int[][] getCompressedTfs() {
      return tfCompressed;
    }

    public int getLastBlockSize() {
      return lastBlockSize;
    }

    public int[][] getCompressedPositions() {
      return positionsCompressed;
    }

    public int getPositionsLastBlockSize() {
      return positionsLastBlockSize;
    }

    /**
     * Compresses a list of integers
     *
     * @param data Array of integers
     * @param blockSize PForDelta block size
     * @param computeGaps Whether to compute gaps before compression
     * @return Compressed arrays
     */
    private static int[][] compressData(int[] data, int blockSize, boolean computeGaps) {
      // Data is stored in blocks of equal size..
      int nbBlocks = (int) Math.ceil(((double) data.length) / ((double) blockSize));
      int[][] compressedBlocks = new int[nbBlocks][];

      int[] temp = new int[blockSize];

      // Compress all blocks except for the last block which might
      // contain fewer elements.
      for(int i = 0; i < nbBlocks - 1; i++) {
        if(!computeGaps) {
          for(int j = 0; j < temp.length; j++) {
            temp[j] = data[i * blockSize + j];
          }
        } else {
          temp[0] = data[i * blockSize];
          int pre = temp[0];
          for(int j = 1; j < temp.length; j++) {
            temp[j] = data[i * blockSize + j] - pre;
            pre = data[i * blockSize + j];
          }
        }
        compressedBlocks[i] = PForDelta.compressOneBlockOpt(temp, blockSize);
      }

      // Compress the last block
      int remaining = computeLastBlockSize(data.length, nbBlocks, blockSize);
      temp = new int[remaining];
      if(!computeGaps) {
        for(int j = 0; j < temp.length; j++) {
          temp[j] = data[(nbBlocks - 1) * blockSize + j];
        }
      } else {
        temp[0] = data[(nbBlocks - 1) * blockSize];
        int pre = temp[0];
        for(int j = 1; j < temp.length; j++) {
          temp[j] = data[(nbBlocks - 1) * blockSize + j] - pre;
          pre = data[(nbBlocks - 1) * blockSize + j];
        }
      }
      compressedBlocks[nbBlocks - 1] = PForDelta.compressOneBlockOpt(temp, remaining);

      return compressedBlocks;
    }

    private static int computeLastBlockSize(int dataLength, int nbBlocks, int blockSize) {
      return dataLength - ((nbBlocks - 1) * blockSize);
    }
  }
}
