/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.iceberg;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.netflix.iceberg.types.Types;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static com.netflix.iceberg.types.Types.NestedField.required;

public class TableTestBase {
  // Schema passed to create tables
  static final Schema SCHEMA = new Schema(
      required(3, "id", Types.IntegerType.get()),
      required(4, "data", Types.StringType.get())
  );

  // Partition spec used to create tables
  static final PartitionSpec SPEC = PartitionSpec.builderFor(SCHEMA)
      .bucket("data", 16)
      .build();

  static final DataFile FILE_A = DataFiles.builder(SPEC)
      .withPath("/path/to/data-a.parquet")
      .withFileSizeInBytes(0)
      .withPartitionPath("data_bucket=0") // easy way to set partition data for now
      .withRecordCount(0)
      .build();
  static final DataFile FILE_B = DataFiles.builder(SPEC)
      .withPath("/path/to/data-b.parquet")
      .withFileSizeInBytes(0)
      .withPartitionPath("data_bucket=1") // easy way to set partition data for now
      .withRecordCount(0)
      .build();
  static final DataFile FILE_C = DataFiles.builder(SPEC)
      .withPath("/path/to/data-c.parquet")
      .withFileSizeInBytes(0)
      .withPartitionPath("data_bucket=2") // easy way to set partition data for now
      .withRecordCount(0)
      .build();
  static final DataFile FILE_D = DataFiles.builder(SPEC)
      .withPath("/path/to/data-d.parquet")
      .withFileSizeInBytes(0)
      .withPartitionPath("data_bucket=3") // easy way to set partition data for now
      .withRecordCount(0)
      .build();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  File tableDir = null;
  File metadataDir = null;
  TestTables.TestTable table = null;

  @Before
  public void setupTable() throws Exception {
    this.tableDir = temp.newFolder();
    tableDir.delete(); // created by table create

    this.metadataDir = new File(tableDir, "metadata");
    this.table = create(SCHEMA, SPEC);
  }

  @After
  public void cleanupTables() throws Exception {
    TestTables.clearTables();
  }

  List<File> listMetadataFiles(String ext) {
    return listMetadataFiles(tableDir, ext);
  }

  List<File> listMetadataFiles(File tableDir, String ext) {
    return Lists.newArrayList(new File(tableDir, "metadata").listFiles(
        (dir, name) -> Files.getFileExtension(name).equalsIgnoreCase(ext)));
  }

  TestTables.TestTable create(Schema schema, PartitionSpec spec) {
    return TestTables.create(tableDir, "test", schema, spec);
  }

  TestTables.TestTable load() {
    return TestTables.load(tableDir, "test");
  }

  TableMetadata readMetadata() {
    return TestTables.readMetadata("test");
  }

  void validateSnapshot(Snapshot old, Snapshot snap, DataFile... newFiles) {
    List<String> oldManifests = old != null ? old.manifests() : ImmutableList.of();

    // copy the manifests to a modifiable list and remove the existing manifests
    List<String> newManifests = Lists.newArrayList(snap.manifests());
    for (String oldManifest : oldManifests) {
      Assert.assertTrue("New snapshot should contain old manifests",
          newManifests.remove(oldManifest));
    }

    Assert.assertEquals("Should create 1 new manifest and reuse old manifests",
        1, newManifests.size());
    String manifest = newManifests.get(0);

    long id = snap.snapshotId();
    Iterator<String> newPaths = paths(newFiles).iterator();

    for (ManifestEntry entry : ManifestReader.read(com.netflix.iceberg.Files.localInput(manifest)).entries()) {
      DataFile file = entry.file();
      Assert.assertEquals("Path should match expected", newPaths.next(), file.path().toString());
      Assert.assertEquals("File's snapshot ID should match", id, entry.snapshotId());
    }

    Assert.assertFalse("Should find all files in the manifest", newPaths.hasNext());
  }

  List<String> paths(DataFile... dataFiles) {
    List<String> paths = Lists.newArrayListWithExpectedSize(dataFiles.length);
    for (DataFile file : dataFiles) {
      paths.add(file.path().toString());
    }
    return paths;
  }
}
