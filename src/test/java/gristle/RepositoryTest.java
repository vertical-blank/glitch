package gristle;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import gristle.GitRepository.Branch;
import gristle.GitRepository.Dir;
import gristle.GitRepository.Ident;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.junit.Test;

public class RepositoryTest {
  
  private Ident ident = new GitRepository.Ident("Ident", "Ident@Ident.com");

  private File parepareDirectory() {
    File repoDir = new File(System.getProperty("java.io.tmpdir"), "tmpRepo.git");
    repoDir.mkdir();
    return repoDir;
  }
  
  private Git prepareGit(File repoDir) throws Exception {
    return Git.init().setGitDir(repoDir).setBare(true).call();
  }
  
  private void cleanUpRepo(Git git) throws Exception {
    org.apache.commons.io.FileUtils.deleteDirectory(git.getRepository().getDirectory());
  }
  
  @Test
  public void commitOnce() throws Exception {
    File repoDir = parepareDirectory();
    Git git = prepareGit(repoDir);
    
    GitRepository repo = new GitRepository(repoDir, ident).initialize("initial commit");
    
    Branch master  = repo.branch("master");
    Branch develop = master.createNewBranch("develop");
    
    Dir root = new Dir();
    String updateContent = "updated";
    root.put("README.md", updateContent.getBytes());
    develop.commit(root, "test commit");
    
    InputStream stream = develop.head().getStream("README.md");
    
    String contentFromGit = streamToString(stream);
    assertEquals(contentFromGit, updateContent);
    
    Set<String> branchNames = new HashSet<String>();
    for (Branch branch : repo.listBranches()) {
      branchNames.add(branch.name);
    }
    assertEquals(new HashSet<String>(Arrays.asList("master", "develop")), branchNames);
    
    assertEquals(Collections.emptyList(), master.head().listFiles());
    
    // clean up.
    cleanUpRepo(git);
  }
  
  @Test
  public void commitTwice() throws Exception {
    File repoDir = new File(System.getProperty("java.io.tmpdir"), "commitTwice");
    
    Git git = Git.init().setDirectory(repoDir).setBare(false).call();
    git.close();
    
    GitRepository repo = new GitRepository(new File(repoDir, ".git"), ident).initialize("README.md", "initial".getBytes(), "initial commit");
    
    Branch master  = repo.branch("master");
    Branch develop = master.createNewBranch("develop");
    Thread.sleep(1000);
    
    Dir root = new Dir();
    String firstContent = "first";
    root.put("README.md", firstContent.getBytes());
    develop.commit(root, "first commit");
    Thread.sleep(1000);
    
    root = new Dir();
    
    String secondContent = "second";
    String anotherContent = "another";
    root.put("ANOTHER.md", anotherContent.getBytes());
    root.put("README.md", secondContent.getBytes());
    develop.commit(root, "second commit");
    Thread.sleep(1000);
    
    assertEquals(streamToString(develop.head().getStream("README.md")), secondContent);
    assertEquals(streamToString(develop.head().getStream("ANOTHER.md")), anotherContent);
    
    assertEquals(streamToString(master.head().getStream("README.md")), "initial");
    
    // clean up.
    cleanUpRepo(git);
  }
  
  @Test
  public void commitNestedDirectory() throws Exception {
    File repoDir = parepareDirectory();
    Git git = prepareGit(repoDir);
    
    GitRepository repo = new GitRepository(repoDir, ident).initialize("README.md", "initial".getBytes(), "initial commit");
    
    Branch master  = repo.branch("master");
    Branch develop = master.createNewBranch("develop");
    
    Dir root = new Dir();
    
    String firstContent = "dirctorieeeeeeeees";
    root.put("README.md", firstContent.getBytes());
    
    Dir dir1 = new Dir("child1");
    Dir dir2 = new Dir("child2");
    Dir dir1_1 = new Dir("child1-child1");
    Dir dir1_2 = new Dir("child1-child2");
    Dir dir2_1 = new Dir("child2-child1");
    Dir dir2_2 = new Dir("child2-child2");
    root.put(dir1).put(dir2);
    dir1.put(dir1_1).put(dir1_2);
    dir2.put(dir2_1).put(dir2_2);
    
    
    dir1.put("1.md", "1__1".getBytes());
    dir1.put("2.md", "1__2".getBytes());
    dir2.put("1.md", "2__1".getBytes());
    dir2.put("2.md", "2__2".getBytes());
    
    dir1_1.put("1.md", "1_1__1".getBytes());
    dir1_1.put("2.md", "1_1__2".getBytes());
    dir1_2.put("1.md", "1_2__1".getBytes());
    dir1_2.put("2.md", "1_2__2".getBytes());
    dir2_1.put("1.md", "2_1__1".getBytes());
    dir2_1.put("2.md", "2_1__2".getBytes());
    dir2_2.put("1.md", "2_2__1".getBytes());
    dir2_2.put("2.md", "2_2__2".getBytes());
    
    develop.commit(root, "dirctories commit");
    
    assertEquals(
      new HashSet<String>(develop.head().listFiles()),
      new HashSet<String>(Arrays.asList(
        "README.md",
        "child1/1.md",
        "child1/2.md",
        "child1/child1-child1/1.md",
        "child1/child1-child1/2.md",
        "child1/child1-child2/1.md",
        "child1/child1-child2/2.md",
        "child2/1.md",
        "child2/2.md",
        "child2/child2-child1/1.md",
        "child2/child2-child1/2.md",
        "child2/child2-child2/1.md",
        "child2/child2-child2/2.md"
      ))
    );

    assertEquals(streamToString(develop.head().getStream("README.md")), "dirctorieeeeeeeees");
    assertEquals(streamToString(develop.head().getStream("child1/1.md")), "1__1");
    assertEquals(streamToString(develop.head().getStream("child2/2.md")), "2__2");
    assertEquals(streamToString(develop.head().getStream("child1/child1-child1/1.md")), "1_1__1");
    assertEquals(streamToString(develop.head().getStream("child2/child2-child2/2.md")), "2_2__2");
    
    Dir dir = develop.head().getDir();
    
    assertEquals(new String(dir.file("README.md").bytes()), "dirctorieeeeeeeees");
    assertEquals(new String(dir.dir("child1").file("1.md").bytes()), "1__1");
    assertEquals(new String(dir.dir("child2").file("2.md").bytes()), "2__2");
    assertEquals(new String(dir.dir("child1").dir("child1-child1").file("1.md").bytes()), "1_1__1");
    assertEquals(new String(dir.dir("child2").dir("child2-child2").file("2.md").bytes()), "2_2__2");
    
    // clean up.
    cleanUpRepo(git);
  }
  
  @Test
  public void commitAndMergeSimple() throws Exception {
    File repoDir = new File(System.getProperty("java.io.tmpdir"), "commitAndMergeSimple");
    repoDir.mkdir();
    
    Git git = Git.init().setDirectory(repoDir).setBare(false).call();
    git.close();
    
    GitRepository repo = new GitRepository(new File(repoDir, ".git"), ident).initialize("README.md", "initial".getBytes(), "initial commit");
    
    Branch master  = repo.branch("master");
    
    Branch develop = master.createNewBranch("develop");
    
    String updateContent = "updated";
    develop.commit(new Dir().put("README.md", updateContent.getBytes()), "test commit");
    assertEquals(streamToString(develop.head().getStream("README.md")), updateContent);
    
    develop.mergeTo(master);
    
    assertEquals(streamToString(master.head().getStream("README.md")), updateContent);
    assertTrue(develop.exists());
    
    // clean up.
    cleanUpRepo(git);
  }
  
  @Test
  public void commitAndMergeConf() throws Exception {
    File repoDir = new File(System.getProperty("java.io.tmpdir"), "commitAndMergeConf");
    repoDir.mkdir();
    
    Git git = Git.init().setDirectory(repoDir).setBare(false).call();
    git.close();
    
    GitRepository repo = new GitRepository(new File(repoDir, ".git"), ident).initialize("README.md", "initial\r\ncommit".getBytes(), "initial commit");
    
    Branch master  = repo.branch("master");
    
    Branch develop = master.createNewBranch("develop");
    
    String updateContent = "initial\r\ncommit\r\nadded";
    develop.commit(new Dir().put("README.md", updateContent.getBytes()), "dev commit");
    assertArrayEquals(develop.head().getDir().file("README.md").bytes(), updateContent.getBytes());
    
    String masterContent = "updated\r\ncommit";
    master.commit(new Dir().put("README.md", masterContent.getBytes()), "second commit");
    
    System.out.println(new String(master.head().getDir().file("README.md").bytes()));
    
    boolean mergeTo = develop.mergeTo(master);
    assertFalse(mergeTo);
    
    // assertArrayEquals(master.head().getDir().file("README.md"), "updated\r\nadded".getBytes());
    
    // clean up.
    cleanUpRepo(git);
  }
  
  private String streamToString(InputStream stream) throws Exception {
    String contentFromGit = null;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))){
      StringBuilder sb = new StringBuilder();
      while(true){
        String line = br.readLine();
        if (line == null){
          break;
        }
        sb.append(line);
      }
      contentFromGit = sb.toString();
    }
    return contentFromGit;
  }
  
}
