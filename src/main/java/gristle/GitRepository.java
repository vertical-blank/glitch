package gristle;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectLoader.SmallObject;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * Jgit Lowlevel-api repository Wrapper.
 * 
 * @author yohei224
 */
public class GitRepository {
  
  private static final String MASTER = "master";
  
  /** Repository */
  private Repository repo;
  
  /**
   * Constructor
   * @param dir git workdirectory
   * @throws IOException
   */
  private GitRepository(File dir) throws IOException {
    this.repo = new FileRepositoryBuilder()
                .setMustExist(true)
                .setGitDir(dir).build();
  }
  
  /**
   * Create and init git bare-repository
   * @param dir
   * @return
   * @throws IOException
   */
  public static GitRepository getInstance(File dir) throws IOException {
    RepositoryBuilder builder = new RepositoryBuilder();
    builder.setBare();
    builder.setGitDir(dir);
    Repository repo = builder.build();
    
    if (!repo.getObjectDatabase().exists()){
      repo.create(true);
    }
    
    return new GitRepository(dir);
  }

  public File getDirectory() {
    return this.repo.getDirectory();
  }
  
  /**
   * Close
   */
  public void close() {
    this.repo.close();
  }
  
  /**
   * Initialize master branch with a file
   * @param filename
   * @param filecontent
   * @param comment
   * @param ident 
   * @return
   * @throws IOException
   */
  public GitRepository initialize(String filename, byte[] filecontent, String comment, Ident ident) throws IOException {
    return this.initialize(MASTER, filename, filecontent, comment, ident);
  }
  
  /**
   * Initialize branch with file
   * @param mastername
   * @param filename
   * @param filecontent
   * @param comment
   * @param ident 
   * @return
   * @throws IOException
   */
  public GitRepository initialize(String mastername, String filename, byte[] filecontent, String comment, Ident ident) throws IOException {
    Branch master = this.branch(mastername);
    Dir root = new Dir().put(filename, filecontent);
    master.commit(root, comment, ident);
    return this;
  }
  
  /**
   * Initialize master branch as blank
   * @param comment
   * @param ident 
   * @return
   * @throws IOException
   */
  public GitRepository initialize(String comment, Ident ident) throws IOException {
    return this.initialize(MASTER, comment, ident);
  }
  
  /**
   * Initialize branch as blank
   * @param mastername
   * @param comment
   * @param ident 
   * @return
   * @throws IOException
   */
  public GitRepository initialize(String mastername, String comment, Ident ident) throws IOException {
    Branch master = this.branch(mastername);
    master.commit(new Dir(), comment, ident);
    return this;
  }
  
  /**
   * List all branches of this repo.
   * @return all branches.
   * @throws IOException
   */
  public List<Branch> listBranches() throws IOException {
    Collection<Ref> values = this.repo.getRefDatabase().getRefs(Constants.R_HEADS).values();
    
    List<Branch> list = new ArrayList<Branch>();
    for (Ref ref : values) {
      list.add(this.branch(ref.getName().substring(Constants.R_HEADS.length())));
    }
    
    return list;
  }
  
  /**
   * get branch instance by name.
   * @param branchName
   * @return
   */
  public Branch branch(String branchName){
    return new Branch(branchName);
  }
  
  /** Branch */
  public class Branch {
    
    private final Repository repo = GitRepository.this.repo;
    
    /** name */
    public final String name;
    
    /**
     * Constructor
     * @param branchName
     */
    public Branch(String name) {
      this.name = name;
    }
    
    /**
     * Returns head of this branch.
     * @return
     * @throws IOException 
     * @throws IncorrectObjectTypeException 
     * @throws MissingObjectException 
     */
    public Commit head() throws MissingObjectException, IncorrectObjectTypeException, IOException {
      Ref head = this.findHeadRef();
      
      if (head == null){
        return null;
      }
      
      try (RevWalk walk = new RevWalk(this.repo)) {
        RevCommit commit = walk.parseCommit(head.getObjectId());
        return new Commit(commit);
      }
    }
    
    /**
     * Returns is this branch exists.
     * @return
     * @throws MissingObjectException
     * @throws IncorrectObjectTypeException
     * @throws IOException
     */
    public boolean exists() throws MissingObjectException, IncorrectObjectTypeException, IOException {
      return this.head() != null;
    }
    
    /**
     * Find head ref
     * @return
     * @throws IOException
     */
    private Ref findHeadRef() throws IOException {
      return this.repo.exactRef(Constants.R_HEADS + this.name);
    }
    
    /**
     * List all commits of this branch.
     * @return all commits.
     * @throws MissingObjectException
     * @throws IncorrectObjectTypeException
     * @throws IOException
     */
    public List<Commit> listCommits() throws MissingObjectException, IncorrectObjectTypeException, IOException {
      Ref head = this.findHeadRef();
      
      try (RevWalk walk = new RevWalk(this.repo)) {
        RevCommit commit = walk.parseCommit(head.getObjectId());
        
        walk.markStart(commit);
        
        List<Commit> revs = new ArrayList<Commit>();
        for (RevCommit rev : walk) {
          revs.add(new Commit(rev));
        }
        walk.dispose();
        
        return revs;
      }
    }
    
    /**
     * Format entries recursively.
     * @param dir dir instance
     * @param inserter ObjectInserter
     * @return treeFormatter contains all entries.
     * @throws IOException
     */
    private TreeFormatter formatDir(Dir dir, ObjectInserter inserter) throws IOException {
      TreeFormatter formatter = new TreeFormatter();
      
      for (Entry<String, Blob> entry : dir.files.entrySet()){
        Blob blob = entry.getValue();
        ObjectId objId = inserter.insert(Constants.OBJ_BLOB, blob.length(), blob.inputStream());
        formatter.append(entry.getKey(), FileMode.REGULAR_FILE, objId);
      }
      
      for (Map.Entry<String, Dir> entry : dir.dirs.entrySet()){
        TreeFormatter dirFormatter = formatDir(entry.getValue(), inserter);
        ObjectId objId = inserter.insert(dirFormatter);
        formatter.append(entry.getKey(), FileMode.TREE, objId);
      }
      
      return formatter;
    }
    

    /**
     * Execute commit to this branch. 
     * @param add
     * @param message
     * @param ident 
     * @return
     * @throws IOException
     */
    public Result commit(Dir add, String message, Ident ident) throws IOException {
      return commit(add, new Dir(), message, ident);
    }
    
    /**
     * Execute commit to this branch.
     * @param add
     * @param rm
     * @param message commit message
     * @param ident 
     * @return
     * @throws IOException
     */
    public Result commit(Dir add, Dir rm, String message, Ident ident) throws IOException {
      PersonIdent personIdent = ident.toPersonIdent();
      
      try (ObjectInserter inserter = this.repo.newObjectInserter()) {
        TreeFormatter formatter = formatDir(add, inserter);
        ObjectId treeId = inserter.insert(formatter);
        
        Commit head = this.head();
        List<ObjectId> parentIds = head != null ? Arrays.asList(head.getId()) : Collections.<ObjectId>emptyList();
        
        CommitBuilder newCommit = new CommitBuilder();
        newCommit.setCommitter(personIdent);
        newCommit.setAuthor(personIdent);
        newCommit.setMessage(message);
        newCommit.setParentIds(parentIds);
        newCommit.setTreeId(treeId);
        
        ObjectId newHeadId = inserter.insert(newCommit);
        inserter.flush();
        inserter.close();
        
        Result updateResult = this.updateTo(newHeadId);
        
        if (updateResult == Result.FAST_FORWARD){
          this.repo.writeMergeCommitMsg(null);
          this.repo.writeMergeHeads(null);
        }
        
        return updateResult;
      }
    }
    
    /**
     * Update head to new commit.
     * @param newCommitId
     * @return
     * @throws IOException
     */
    private Result updateTo(ObjectId newCommitId) throws IOException{
      Commit head = this.head();
      ObjectId oldHeadId = head != null ? head.getId() : ObjectId.zeroId();
      
      RefUpdate refUpdate = this.repo.updateRef(Constants.R_HEADS + this.name);
      refUpdate.setNewObjectId(newCommitId);
      refUpdate.setExpectedOldObjectId(oldHeadId);
      return refUpdate.update();
    }
    
    /**
     * Delete this branch.
     * @return
     * @throws IOException
     */
    public Result delete() throws IOException {
      RefUpdate refDelete = this.repo.updateRef(Constants.R_HEADS + this.name);
      refDelete.setRefLogMessage("branch deleted", false);
      refDelete.setForceUpdate(true);
      return refDelete.delete();
    }
    
    /**
     * Merge this branch into another one, and then delete this.
     * @param toBranch
     * @param ident 
     * @return
     * @throws IOException
     */
    public boolean mergeTo(Branch toBranch, Ident ident) throws IOException {
      return mergeTo(toBranch, false, ident);
    }
    
    /**
     * Merge this branch into another one.
     * @param toBranch
     * @param delete
     * @param ident 
     * @return
     * @throws IOException
     */
    public boolean mergeTo(Branch toBranch, boolean delete, Ident ident) throws IOException {
      PersonIdent personIdent = ident.toPersonIdent();
      ObjectInserter inserter = this.repo.newObjectInserter();
      
      try (RevWalk revWalk = new RevWalk(this.repo)) {
        RevCommit srcCommit = revWalk.parseCommit(this.findHeadRef().getObjectId());
        
        Ref toHeadRef = toBranch.findHeadRef();
        RevCommit toCommit  = revWalk.parseCommit(toHeadRef.getObjectId());
        
        this.repo.writeMergeCommitMsg("mergeMessage");
        this.repo.writeMergeHeads(Arrays.asList(this.repo.exactRef(Constants.HEAD).getObjectId()));
        
        Merger merger = MergeStrategy.RECURSIVE.newMerger(this.repo, true);
        boolean merge = merger.merge(srcCommit, toCommit);
        if (!merge){
          return false;
        }
        
        ObjectId mergeResultTreeId = merger.getResultTreeId();
        
        CommitBuilder newCommit = new CommitBuilder();
        newCommit.setCommitter(personIdent);
        newCommit.setAuthor(personIdent);
        newCommit.setMessage("merge commit message");
        newCommit.setParentIds(toCommit.getId(), srcCommit.getId());
        newCommit.setTreeId(mergeResultTreeId);
        
        ObjectId newHeadId = inserter.insert(newCommit);
        inserter.flush();
        inserter.close();
        
        toBranch.updateTo(newHeadId);
        
        if (delete){
          this.delete();
        }
        
        return true;
      }
    }
    
    /**
     * Create new branch from this branch.
     * @param newBranchName name of new branch
     * @return instance of new branch
     * @throws IOException
     */
    public Branch createNewBranch(String newBranchName) throws IOException {
      Branch newBranch = GitRepository.this.branch(newBranchName);
      
      Ref findHeadRef = this.findHeadRef();
      
      newBranch.updateTo(findHeadRef.getObjectId());
      
      return newBranch;
    }
    
    /**
     * Wrapper of RevCommit.
     */
    public class Commit {
      
      private final Repository repo = Branch.this.repo;
      
      private RevCommit rev;
      
      /**
       * Constructor
       * @param rev
       */
      public Commit(RevCommit rev){
        this.rev = rev;
      }
      
      /**
       * Returns RevId.
       * @return
       */
      public ObjectId getId() {
        return this.rev.getId();
      }
      
      /**
       * Returns comment.
       * @return
       */
      public String getComment() {
        return this.rev.getShortMessage();
      }
      
      /**
       * Returns parents.
       * @return
       */
      public List<Commit> getParents() {
        List<Commit> commits = new ArrayList<GitRepository.Branch.Commit>();
        for(RevCommit commit : rev.getParents()){
          commits.add(new Commit(commit));
        }
        return commits;
      }
      
      /**
       * Returns structured directories and files.
       * @return
       * @throws MissingObjectException
       * @throws IncorrectObjectTypeException
       * @throws CorruptObjectException
       * @throws IOException
       */
      public Dir getDir() throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
        RevTree tree = this.rev.getTree();
        
        Dir root = new Dir();
        
        try (TreeWalk treeWalk = new TreeWalk(this.repo)){
          treeWalk.addTree(tree);
          this.walkTree(root, treeWalk);
        }
        
        return root;
      }
      
      /**
       * Walkthrough tree recursively.
       * @param dir
       * @param treeWalk
       * @return
       * @throws MissingObjectException
       * @throws IncorrectObjectTypeException
       * @throws CorruptObjectException
       * @throws IOException
       */
      private Dir walkTree(Dir dir, TreeWalk treeWalk) throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
        while (treeWalk.next()){
          if (treeWalk.isPostChildren()){
            return dir;
          }
          if (treeWalk.isSubtree()){
            treeWalk.setPostOrderTraversal(true);
            treeWalk.enterSubtree();
            dir.put(walkTree(new Dir(treeWalk.getNameString()), treeWalk));
          }
          else {
            dir.put(treeWalk.getNameString(), this.repo.open(treeWalk.getObjectId(0)).getBytes());
          }
        }
        return dir;
      }
      
      /**
       * List all filepaths of specified revision.
       * @return
       * @throws IOException
       */
      public List<String> listFiles() throws IOException {
        List<String> list = new ArrayList<String>();
        
        try (RevWalk revWalk = new RevWalk(this.repo)) {
          RevCommit commit = this.rev;
          RevTree tree = revWalk.parseTree(commit.getTree().getId());
          
          try (TreeWalk treeWalk = new TreeWalk(this.repo)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            
            while(treeWalk.next()){
              list.add(treeWalk.getPathString());
            }
          }
        }
        
        return list;
      }
      
      /**
       * Returns inputstream of file contained by head of this brach.
       * @param path
       * @return
       * @throws IOException
       * @throws FileNotFoundException
       */
      public InputStream getStream(String path) throws IOException, FileNotFoundException {
        try (RevWalk revWalk = new RevWalk(this.repo)) {
          RevCommit commit = this.rev;
          RevTree tree = revWalk.parseTree(commit.getTree().getId());
          
          try (TreeWalk treeWalk = new TreeWalk(this.repo)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(path));
            if(!treeWalk.next()){
              throw new FileNotFoundException("Couldnt find file.");
            }
            
            return this.repo.open(treeWalk.getObjectId(0)).openStream();
          }
        }
      }
    }
  }
  
  /** Ident */
  public static class Ident {
    private String name;
    private String mail;
    
    public Ident(String name, String mail) {
      this.name = name;
      this.mail = mail;
    }
    
    /** Convert to jgit navtive ident */
    public PersonIdent toPersonIdent() {
      return new PersonIdent(this.name, this.mail);
    }
  }
  
  /** Direcotry object for commting */
  public static class Dir {
    final String name;
    
    public Map<String, Dir>  dirs  = new TreeMap<String, Dir>();
    public Map<String, Blob> files = new TreeMap<String, Blob>();
    
    public Dir(){
      this.name = "root";
    }
    
    public Dir(String name) {
      this.name = name;
    }
    
    public Dir dir(String name) {
      return this.dirs.get(name);
    }
    
    public Blob file(String name) {
      return this.files.get(name);
    }
    
    public Dir put(String filename, byte[] content) throws IOException {
      this.files.put(filename, new Blob(content));
      return this;
    }
    
    public Dir put(String filename, ObjectLoader loader) throws IOException {
      this.files.put(filename, new Blob(loader));
      return this;
    }
    
    public Dir put(String filename, Blob blob) throws IOException {
      this.files.put(filename, blob);
      return this;
    }
    
    public Dir put(Dir dir) throws IOException {
      this.dirs.put(dir.name, dir);
      return this;
    }
  }
  
  /** Blob as file entry */
  public static class Blob {
    final ObjectLoader loader;
    
    public Blob(ObjectLoader loader) {
      this.loader = loader;
    }
    
    public Blob(byte[] bytes) {
      this.loader = new SmallObject(Constants.OBJ_BLOB, bytes);
    }
    
    public long length() {
      return this.loader.getSize();
    }
    
    public InputStream inputStream() throws MissingObjectException, IOException {
      return this.loader.openStream();
    }
    
    public byte[] bytes() {
      return this.loader.getBytes();
    }
    
  }
  
}
