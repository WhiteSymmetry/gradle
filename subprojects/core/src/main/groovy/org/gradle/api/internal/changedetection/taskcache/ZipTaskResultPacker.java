/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.taskcache;

import com.google.common.io.ByteSink;
import com.google.common.io.Closer;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.FileCollection;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipTaskResultPacker implements TaskResultPacker {
    @Override
    public TaskResultOutput pack(final File rootDir, final FileCollection files) throws IOException {
        return new TaskResultOutput() {
            @Override
            public void writeTo(ByteSink output) throws IOException {
                Closer closer = Closer.create();
                OutputStream outputStream = closer.register(output.openBufferedStream());
                try {
                    ZipOutputStream zipOutput = new ZipOutputStream(outputStream);
                    String rootPath = rootDir.getAbsolutePath();
                    for (File file : files.getFiles()) {
                        String absolutePath = file.getAbsolutePath();
                        if (!absolutePath.startsWith(rootPath)) {
                            throw new IOException(String.format("File %s is outside cache root dir %s", file, rootDir));
                        }
                        String path = absolutePath.substring(rootPath.length() + 1);
                        zipOutput.putNextEntry(new ZipEntry(path));

                        Files.copy(file, zipOutput);
                        zipOutput.closeEntry();
                    }
                    zipOutput.close();
                } catch (Exception e) {
                    throw closer.rethrow(e);
                } finally {
                    //noinspection ThrowFromFinallyBlock
                    closer.close();
                }
            }
        };
    }

    @Override
    public void unpack(File rootDir, TaskResultInput result) throws IOException {
        Closer closer = Closer.create();
        InputStream input = closer.register(result.read().openBufferedStream());
        try {
            ZipInputStream zipInput = new ZipInputStream(input);
            while (true) {
                ZipEntry entry = zipInput.getNextEntry();
                if (entry == null) {
                    break;
                }
                File file = new File(rootDir, entry.getName());
                if (entry.isDirectory()) {
                    FileUtils.forceMkdir(file);
                    continue;
                }
                FileUtils.forceMkdir(file.getParentFile());
                if (file.exists()) {
                    FileUtils.forceDelete(file);
                }
                Files.asByteSink(file).writeFrom(zipInput);
            }
        } catch (Exception e) {
            throw closer.rethrow(e);
        } finally {
            //noinspection ThrowFromFinallyBlock
            closer.close();
        }
    }
}
