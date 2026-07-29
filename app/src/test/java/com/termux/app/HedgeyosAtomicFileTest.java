package com.termux.app;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class HedgeyosAtomicFileTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writeAtomicallyReplacesContentAndLeavesNoTemporaryFile() throws Exception {
        File directory = temporaryFolder.newFolder("state");
        File state = new File(directory, "runtime.state");

        HedgeyosAtomicFile.write(state, "STARTING\n");
        HedgeyosAtomicFile.write(state, "RUNNING\n");

        Assert.assertEquals("RUNNING\n",
            new String(Files.readAllBytes(state.toPath()), StandardCharsets.UTF_8));
        File[] temporaryFiles = directory.listFiles(
            file -> file.getName().startsWith("runtime.state.tmp."));
        Assert.assertNotNull(temporaryFiles);
        Assert.assertEquals(0, temporaryFiles.length);
    }
}
