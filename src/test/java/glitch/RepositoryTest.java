package glitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import glitch.GitRepository.Branch;
import glitch.GitRepository.Dir;
import glitch.GitRepository.Ident;
import glitch.GitRepository.Tag;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.Test;

public class RepositoryTest {
  
  private Ident ident = new GitRepository.Ident("Ident", "Ident@Ident.com");

  private File parepareDirectory(String name) throws Exception {
    File dir = new File(System.getProperty("java.io.tmpdir"), name);
    cleanUp(dir);
    dir.mkdir();
    return dir;
  }
  
  private GitRepository prepareGit(String name) throws Exception {
    return GitRepository.getInstance(parepareDirectory(name));
  }
  
  private void cleanUp(GitRepository repo) throws Exception {
    cleanUp(repo.getDirectory());
  }
  
  private void cleanUp(File dir) throws Exception {
    org.apache.commons.io.FileUtils.deleteDirectory(dir);
  }
  
  @Test
  public void commitOnce() throws Exception {
    GitRepository repo = prepareGit("commitOnce.git").initialize("initial commit", ident);
    
    Branch master  = repo.branch("master");
    Branch develop = master.createNewBranch("develop");
    
    Dir root = new Dir();
    String updateContent = "updated";
    root.put("README.md", updateContent.getBytes());
    develop.commit(root, "test commit", ident);
    
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
    cleanUp(repo);
  }
  
  @Test
  public void commitTwice() throws Exception {
    GitRepository repo = prepareGit("commitTwice.git").initialize("README.md", "initial".getBytes(), "initial commit", ident);
    
    Branch master  = repo.branch("master");
    Branch develop = master.createNewBranch("develop");
    Thread.sleep(1000);
    
    Dir root = new Dir();
    String firstContent = "first";
    root.put("README.md", firstContent.getBytes());
    develop.commit(root, "first commit", ident);
    Thread.sleep(1000);
    
    root = new Dir();
    
    String secondContent = "second";
    String anotherContent = "another";
    root.put("ANOTHER.md", anotherContent.getBytes());
    root.put("README.md", secondContent.getBytes());
    develop.commit(root, "second commit", ident);
    Thread.sleep(1000);
    
    assertEquals(streamToString(develop.head().getStream("README.md")), secondContent);
    assertEquals(streamToString(develop.head().getStream("ANOTHER.md")), anotherContent);
    
    assertEquals(streamToString(master.head().getStream("README.md")), "initial");
    
    // clean up.
    cleanUp(repo);
  }
  
  @Test
  public void commitNestedDirectory() throws Exception {
    GitRepository repo = prepareGit("commitNested.git").initialize("README.md", "initial".getBytes(), "initial commit", ident);
    
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
    
    develop.commit(root, "dirctories commit", ident);
    
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
    cleanUp(repo);
  }
  
  @Test
  public void commitAndMergeSimple() throws Exception {
    GitRepository repo = prepareGit("commitAndMergeSimple.git").initialize("README.md", "initial".getBytes(), "initial commit", ident);
    
    Branch master  = repo.branch("master");
    
    Branch develop = master.createNewBranch("develop");
    
    String updateContent = "updated";
    develop.commit(new Dir().put("README.md", updateContent.getBytes()), "test commit", ident);
    assertEquals(streamToString(develop.head().getStream("README.md")), updateContent);
    
    develop.mergeTo(master, ident);
    
    assertEquals(streamToString(master.head().getStream("README.md")), updateContent);
    assertTrue(develop.exists());
    
    // clean up.
    cleanUp(repo);
  }
  
  @Test
  public void mergeConflict() throws Exception {
    String initial = joinLines(
        "AAAAAAA",
        "=======",
        "BBBBBBB",
        "=======",
        "CCCCCCC",
        "CCCCCCC",
        "CCCCCCC",
        "=======",
        "DDDDDDD",
        "=======",
        "EEEEEEE");
    GitRepository repo = prepareGit("mergeConflict.git").initialize("README.md", initial.getBytes(), "initial commit", ident);
    
    Branch master  = repo.branch("master");
    
    Branch develop = master.createNewBranch("develop");
    
    String masterContent = joinLines(
        "AAAAAAA",
        "=======",
        "XXXXXXX",
        "=======",
        "CCCCCC1",
        "CCCCCC2",
        "CCCCCC3",
        "=======",
        "DDDDDDD",
        "=======",
        "EEEEEEE");
    master.commit(new Dir().put("README.md", masterContent.getBytes()), "master commit", ident);

    String developContent = joinLines(
        "AAAAAAA",
        "=======",
        "BBBBBBB",
        "=======",
        "1CCCCCC",
        "2CCCCCC",
        "3CCCCCC",
        "4444444",
        "5555555",
        "=======",
        "YYYYYYY",
        "=======",
        "EEEEEEE");
    develop.commit(new Dir().put("README.md", developContent.getBytes()), "develop commit", ident);
    
    boolean mergable = develop.isMergableTo(master);
    assertFalse(mergable);
    
    List<Map<String, String>> expected = new ArrayList<Map<String,String>>();
    expected.addAll(
      Arrays.asList(
        map(
            pair("BASE", joinLines(
                "AAAAAAA",
                "=======", ""))
        ),
        map(
            pair("master", joinLines(
                "XXXXXXX", ""))
        ),
        map(
            pair("BASE", joinLines(
                "=======", ""))
        ),
        map(
            pair("develop", joinLines(
                "1CCCCCC",
                "2CCCCCC",
                "3CCCCCC",
                "4444444",
                "5555555", "")),
            pair("master", joinLines(
                "CCCCCC1",
                "CCCCCC2",
                "CCCCCC3", ""))
        ),
        map(
            pair("BASE", joinLines(
                "=======", ""))
        ),
        map(
            pair("develop", joinLines(
                "YYYYYYY", ""))
        ),
        map(
            pair("BASE", joinLines(
                "=======",
                "EEEEEEE"))
        )
    ));
    
    Map<String, List<Map<String, String>>> conflicts = develop.getConflicts(master);
    for (Entry<String, List<Map<String, String>>> conflict : conflicts.entrySet()) {
      String fileName = conflict.getKey();
      assertEquals("README.md", fileName);
      List<Map<String, String>> value = conflict.getValue();
      
      assertEquals(expected, value);
    }
    
    
    // clean up.
    cleanUp(repo);
  }
  
  private Map<String, String> map(Pair ... pairs) {
    Map<String, String> map = new HashMap<String, String>();
    for (Pair pair : pairs) {
      map.put(pair.k, pair.v);
    }
    return map;
  }
  
  private class Pair{
    final String k;
    final String v;
    Pair(String k, String v){
      this.k = k;
      this.v = v;
    }
  }
  Pair pair(String k, String v){
    return new Pair(k, v);
  }
  
  @Test
  public void tags() throws Exception {
    GitRepository repo = prepareGit("tags.git").initialize("README.md", "initial".getBytes(), "initial commit", ident);
    
    Branch master  = repo.branch("master");
    Thread.sleep(1000);
    
    master.commit(new Dir().put("README.md", "first".getBytes()), "first commit", ident).addTag("ZZZZ", "ZZZZ", ident);
    Thread.sleep(1000);
    
    master.commit(new Dir().put("README.md", "second".getBytes()), "second commit", ident);
    Thread.sleep(1000);
    
    master.commit(new Dir().put("README.md", "third".getBytes()), "second commit", ident).addTag("AAAA", "AAAA", ident);
    Thread.sleep(1000);
    
    List<String> tagnames = new ArrayList<String>();
    for (Tag tag : repo.listTags()) {
      tagnames.add(tag.name);
    }
    
    assertTrue(Arrays.asList("ZZZZ", "AAAA").equals(tagnames));
    
    // clean up.
    cleanUp(repo);
  }
  
  private String joinLines(String ... lines) {
    StringBuilder sb = new StringBuilder();
    
    boolean first = true;
    for (String line : lines) {
      if (first) {
        first = false;
      }
      else {
        sb.append("\n");
      }
      sb.append(line);
    }
    
    return sb.toString();
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
