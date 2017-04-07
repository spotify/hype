package com.spotify.hype.gcs;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class StagingUtilTest {

  String testFilePath;
  String filenameWithoutExtension;
  Path tmp;
  String stagingPath;

  @Before
  public void setUp() throws Exception {
    URLClassLoader cl = (URLClassLoader) StagingUtilTest.class.getClassLoader();
    File testFile = new File(cl.getURLs()[0].toURI());
    testFilePath = testFile.getAbsolutePath();
    filenameWithoutExtension = testFile.getName().replaceAll("\\.\\w+$", "");

    tmp = Files.createTempDirectory("unit-test");
    stagingPath = tmp.toUri().toString();
  }

  @Test
  public void stagesFileWithHashedFilename() throws Exception {
    StagingUtil.stageClasspathElements(singletonList(testFilePath), stagingPath);

    List<String> list = Files.list(tmp).map(Path::toString).collect(toList());
    assertThat(list, hasSize(1));
    assertThat(list, hasItem(containsString(filenameWithoutExtension)));

    assertThat(list.get(0).matches(".+/" + md5HashPattern()), is(true));
  }

  @Test
  public void detectsAlreadyStagedFiles() throws Exception {
    StagingUtil.stageClasspathElements(singletonList(testFilePath), stagingPath);
    StagingUtil.stageClasspathElements(singletonList(testFilePath), stagingPath);

    List<String> list = Files.list(tmp).map(Path::toString).collect(toList());
    assertThat(list, hasSize(1));
  }

  @Test
  public void returnStagedLocations() throws Exception {
    List<StagingUtil.StagedPackage> stagedPackages =
        StagingUtil.stageClasspathElements(singletonList(testFilePath), stagingPath);

    assertThat(stagedPackages, hasSize(1));
    StagingUtil.StagedPackage stagedPackage = stagedPackages.get(0);

    assertThat(stagedPackage.getName().matches(md5HashPattern()), is(true));
    assertThat(stagedPackage.getLocation().matches(".+/" + md5HashPattern()), is(true));
  }

  private String md5HashPattern() {
    return filenameWithoutExtension + "-.{22}.+";
  }
}
