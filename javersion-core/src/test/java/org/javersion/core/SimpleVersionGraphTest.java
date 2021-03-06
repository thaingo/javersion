/*
 * Copyright 2013 Samppa Saarela
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
package org.javersion.core;

import com.google.common.base.Function;
import com.google.common.collect.*;
import org.javersion.core.SimpleVersion.Builder;
import org.junit.Test;

import java.util.*;

import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.ImmutableSet.copyOf;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.javersion.core.SimpleVersion.builder;
import static org.javersion.core.SimpleVersionGraph.init;
import static org.javersion.core.Version.DEFAULT_BRANCH;

public class SimpleVersionGraphTest {

    private static final Set<Revision> EMPTY_REVISIONS = setOf();

    private static final Map<String, String> EMPTY_PROPERTIES = mapOf();

    private static final String ALT_BRANCH = "alt-branch";

    private static final Revision[] REV = new Revision[50];

    static {
        for (int i=0; i < REV.length; i++) {
            REV[i] = new Revision(i, 0);
        }
    }
    /**
     * <pre>
     *     default
     *        alt-branch
     *     1    firstName: "John", lastName: "Doe"
     *     | \
     *     2  |  status: "Single"
     *     |  |
     *     |  3  mood: "Lonely"
     *     |  |
     *     |  4  lastName: "Foe", status: "Just married", mood: "Ecstatic", married: "2013-10-12"
     *     |  |
     *     5 /   mood: "Ecstatic"
     *   / |/
     *  |  6    mood: null, married: null // unresolved status!
     *  |  |
     *  |  7    // still unresolved status!
     *  |  |
     *  |  8    status="Married" // resolve status
     *  |  |
     *  |  9    type: RESET, status: "New beginning"
     *  |  |
     *  |  |  10  status: Starts with conflict
     *  |  |  |
     *  |  |  11  purpose: "Reset alt-branch"
     *  |  | /
     *   \ 12   Full reset
     *    \|
     *     13  status: "Revert to #5", firstName: "John", lastName: "Doe", mood: "Ecstatic"
     *     |
     *     14  mood: null
     * </pre>
     */
    public static List<VersionExpectation> EXPECTATIONS;

    static {
        ImmutableList.Builder b = ImmutableList.builder();
        b.add(
                when(version(REV[1])
                        .changeset(mapOf(
                                "firstName", "John",
                                "lastName", "Doe")))
                        .expectProperties(mapOf(
                                "firstName", "John",
                                "lastName", "Doe"))
        );
        b.add(
                then("Empty merge")
                        .expectAllHeads(setOf(REV[1]))
                        .mergeRevisions(EMPTY_REVISIONS)
                        .expectProperties(EMPTY_PROPERTIES)
        );
        b.add(
                when(version(REV[2])
                        .parents(setOf(REV[1]))
                        .changeset(mapOf(
                                "status", "Single")))
                        .expectProperties(mapOf(
                                "firstName", "John", // 1
                                "lastName", "Doe", // 1
                                "status", "Single")) // 2
        );
        b.add(
                when(version(REV[3])
                        .branch(ALT_BRANCH)
                        .parents(setOf(REV[1]))
                        .changeset(mapOf(
                                "mood", "Lonely")))
                        .expectAllHeads(setOf(REV[2], REV[3]))
                        .mergeRevisions(setOf(REV[3], REV[2]))
                        .expectProperties(mapOf(
                                "firstName", "John", // 1
                                "lastName", "Doe", // 1
                                "status", "Single", // 2
                                "mood", "Lonely")) // 3
        );
        b.add(
                when(version(REV[4])
                        .branch(ALT_BRANCH)
                        .parents(setOf(REV[3]))
                        .changeset(mapOf(
                                "lastName", "Foe",
                                "status", "Just married",
                                "mood", "Ecstatic",
                                "married", "2013-10-12")))
                        .expectAllHeads(setOf(REV[2], REV[4]))
                        .mergeRevisions(setOf(REV[4]))
                        .expectProperties(mapOf(
                                "firstName", "John", // 1
                                "lastName", "Foe", // 4
                                "status", "Just married", // 4
                                "mood", "Ecstatic", // 4
                                "married", "2013-10-12")) // 4
        );
        b.add(
                then("Merge with ancestor")
                        .mergeRevisions(setOf(REV[3], REV[4]))
                        .expectMergeHeads(setOf(REV[4]))
                        .expectProperties(mapOf(
                                "firstName", "John", // 1
                                "lastName", "Foe", // 4
                                "status", "Just married", // 4
                                "mood", "Ecstatic", // 4
                                "married", "2013-10-12")) // 4
        );
        b.add(
                then("Merge with concurrent older version")
                        .mergeRevisions(setOf(REV[2], REV[4]))
                        .expectMergeHeads(setOf(REV[2], REV[4]))
                        .expectProperties(mapOf(
                                "firstName", "John", // 1
                                "lastName", "Foe", // 4
                                "status", "Just married", // 4
                                "mood", "Ecstatic", // 4
                                "married", "2013-10-12")) // 4
                        .expectConflicts(multimapOf(
                                "status", "Single" // 2
                        ))
        );
        b.add(
                then("Merge with concurrent older version, ignore order")
                        .mergeRevisions(setOf(REV[4], REV[2]))
                        .expectMergeHeads(setOf(REV[2], REV[4]))
                        .expectProperties(mapOf(
                                "firstName", "John", // 1
                                "lastName", "Foe", // 4
                                "status", "Just married", // 4
                                "mood", "Ecstatic", // 4
                                "married", "2013-10-12")) // 4
                        .expectConflicts(multimapOf(
                                "status", "Single" // 2
                        ))
        );
        b.add(
                when(version(REV[5])
                        .parents(setOf(REV[2]))
                        .changeset(mapOf(
                                "mood", "Ecstatic")))
                        .expectAllHeads(setOf(REV[5], REV[4]))
                        .mergeRevisions(setOf(REV[4], REV[5]))
                        .expectProperties(mapOf(
                                "firstName", "John",
                                "lastName", "Foe",
                                "status", "Just married", // 4
                                "mood", "Ecstatic", // 4 and 5 - not conflicting!
                                "married", "2013-10-12")) // 4
                        .expectConflicts(multimapOf(
                                "status", "Single" // 2
                        ))
        );
        b.add(
                then("Merge default branch")
                        .mergeBranches(setOf(DEFAULT_BRANCH))
                        .expectMergeHeads(setOf(REV[5]))
                        .expectProperties(mapOf(
                                "firstName", "John", // 1
                                "lastName", "Doe", // 1
                                "status", "Single", // 2
                                "mood", "Ecstatic")) // 5
        );
        b.add(
                then("Merge alt-branch")
                        .mergeBranches(setOf(ALT_BRANCH))
                        .expectMergeHeads(setOf(REV[4]))
                        .expectProperties(mapOf(
                                "firstName", "John", // 1
                                "lastName", "Foe", // 4
                                "status", "Just married", // 4
                                "mood", "Ecstatic", // 5
                                "married", "2013-10-12")) // 4
        );
        b.add(
                when(version(REV[6])
                        .parents(setOf(REV[5], REV[4]))
                        .changeset(mapOf(
                                "mood", null,
                                "married", null)))
                        .expectAllHeads(setOf(REV[6], REV[4]))
                        .expectProperties(mapOf(
                                "firstName", "John",
                                "lastName", "Foe",
                                "status", "Just married")) // 4
                        .expectConflicts(multimapOf(
                                "status", "Single" // 2 - unresolved conflict
                        ))
        );
        b.add(
                then("Merge alt-branch - should not have changed")
                        .mergeBranches(setOf(ALT_BRANCH))
                        .expectMergeHeads(setOf(REV[4]))
                        .expectProperties(mapOf(
                                "firstName", "John", // 1
                                "lastName", "Foe", // 4
                                "status", "Just married", // 4
                                "mood", "Ecstatic", // 4
                                "married", "2013-10-12")) // 4
        );
        b.add(
                then("Merge default to alt-branch")
                        .mergeBranches(setOf(ALT_BRANCH, DEFAULT_BRANCH))
                        .expectMergeHeads(setOf(REV[6]))
                        .expectProperties(mapOf(
                                "firstName", "John",
                                "lastName", "Foe",
                                "status", "Just married")) // 4
                        .expectConflicts(multimapOf(
                                "status", "Single" // 2 - unresolved conflict
                        ))
        );
        b.add(
                then("Merge alt-branch to default")
                        .mergeBranches(setOf(DEFAULT_BRANCH, ALT_BRANCH))
                        .expectMergeHeads(setOf(REV[6]))
                        .expectProperties(mapOf(
                                "firstName", "John",
                                "lastName", "Foe",
                                "status", "Just married")) // 4
                        .expectConflicts(multimapOf(
                                "status", "Single" // 2 - unresolved conflict
                        ))
        );
        b.add(
                when(version(REV[7])
                        .parents(setOf(REV[6])))
                        .expectProperties(mapOf(
                                "firstName", "John",
                                "lastName", "Foe",
                                "status", "Just married"))
                        .expectConflicts(multimapOf(
                                "status", "Single" // 2 - still unresolved conflict
                        ))
        );
        b.add(
                when(version(REV[8])
                        .parents(setOf(REV[7]))
                        .changeset(mapOf(
                                "status", "Married"
                        )))
                        .expectProperties(mapOf(
                                "firstName", "John",
                                "lastName", "Foe",
                                "status", "Married"))
        );
        b.add(
                when(version(REV[9])
                        .parents(setOf(REV[8]))
                        .changeset(mapOf(
                                "status", "New beginning"))
                        .type(VersionType.RESET))
                        .expectAllHeads(setOf(REV[9]))
                        .expectMergeHeads(setOf(REV[9]))
                        .expectProperties(mapOf(
                                "status", "New beginning"))
        );
        b.add(
                when(version(REV[10])
                        .branch(ALT_BRANCH)
                        .changeset(mapOf("status", "Starts with conflict")))
                        .mergeBranches(setOf(DEFAULT_BRANCH, ALT_BRANCH))
                        .expectAllHeads(setOf(REV[9], REV[10]))
                        .expectMergeHeads(setOf(REV[9], REV[10]))
                        .expectProperties(mapOf(
                                "status", "New beginning"
                        ))
                        .expectConflicts(multimapOf(
                                "status", "Starts with conflict"
                        ))
        );
        b.add(
                when(version(REV[11])
                        .parents(setOf(REV[10]))
                        .changeset(mapOf(
                                "purpose", "Reset alt-branch"))
                        .type(VersionType.RESET))
                        .mergeBranches(setOf(DEFAULT_BRANCH))
                        .expectAllHeads(setOf(REV[9], REV[11]))
                        .expectMergeHeads(setOf(REV[9], REV[11]))
                        .expectProperties(mapOf(
                                "status", "New beginning",
                                "purpose", "Reset alt-branch"))
        );
        b.add(
                when(version(REV[12]) // Full reset
                        .parents(setOf(REV[11], REV[9]))
                        .type(VersionType.RESET))
                        .expectAllHeads(setOf(REV[12]))
                        .expectProperties(mapOf())
        );
        b.add(
                when(version(REV[13])
                        .parents(setOf(REV[12], REV[5]))
                        .changeset(mapOf(
                                "status", "Revert to #5")))
                        .mergeBranches(setOf(DEFAULT_BRANCH))
                        .expectAllHeads(setOf(REV[13]))
                        .expectMergeHeads(setOf(REV[13]))
                        .expectProperties(mapOf(
                                "status", "Revert to #5",
                                "firstName", "John",
                                "lastName", "Doe",
                                "mood", "Ecstatic"))
        );
        b.add(
                when(version(REV[14])
                        .parents(setOf(REV[13]))
                        .changeset(mapOf(
                                "mood", null)))
                        .mergeBranches(setOf(DEFAULT_BRANCH))
                        .expectAllHeads(setOf(REV[14]))
                        .expectMergeHeads(setOf(REV[14]))
                        .expectProperties(mapOf(
                                "status", "Revert to #5",
                                "firstName", "John",
                                "lastName", "Doe"))
        );

        EXPECTATIONS = b.build();
    }

    @Test
    public void Sequential_Updates() {
        SimpleVersionGraph versionGraph = init();
        Revision revision = null;
        for (VersionExpectation expectation : EXPECTATIONS) {
            if (expectation.version != null) {
                revision = expectation.version.revision;
                versionGraph = versionGraph.commit(expectation.version);
            }
            assertGraphExpectations(versionGraph, revision, expectation);
            assertMergeExpectations(versionGraph, revision, expectation);
        }
    }

    static List<List<VersionExpectation>> getBulkExpectations() {
        List<List<VersionExpectation>> bulks = newArrayList();
        for (int i=1; i<= EXPECTATIONS.size(); i++) {
            bulks.add(EXPECTATIONS.subList(0, i));
        }
        return bulks;
    }

    @Test
    public void Bulk_Init() {
        Revision revision = null;
        for (List<VersionExpectation> expectations : getBulkExpectations()) {
            VersionExpectation expectation = expectations.get(expectations.size() - 1);
            if (expectation.getRevision() != null) {
                revision = expectation.getRevision();
            }
            SimpleVersionGraph versionGraph = init(getVersions(expectations));
            assertGraphExpectations(versionGraph, revision, expectation);
            assertMergeExpectations(versionGraph, revision, expectation);
        }
    }

    @Test
    public void Visit_Older_Versions() {
        SimpleVersionGraph versionGraph = init(getVersions(EXPECTATIONS));
        runExpectations(versionGraph, EXPECTATIONS);
    }

    @Test
    public void Bulk_Commit() {
        SimpleVersionGraph versionGraph = init(EXPECTATIONS.get(0).version);
        versionGraph = versionGraph.commit(getVersions(EXPECTATIONS.subList(1, EXPECTATIONS.size())));
        runExpectations(versionGraph, EXPECTATIONS);
    }

    @Test
    public void Tip_of_an_Empty_Graph() {
        SimpleVersionGraph versionGraph = init();
        assertThat(versionGraph.getTip()).isNull();
        assertThat(newArrayList(versionGraph.getVersions())).isEmpty();
    }

    @Test(expected = VersionNotFoundException.class)
    public void Version_Not_Found() {
        init().getVersionNode(new Revision());
    }

    @Test
    public void VersionNode_getChangeset_should_not_throw_NPE() {
        SimpleVersion v1 = new Builder()
                .changeset(mapOf("key", "value", "null", null))
                .build();

        SimpleVersionGraph versionGraph = init(asList(v1));
        VersionNode versionNode = versionGraph.getVersionNode(v1.revision);
        assertThat(versionNode.getChangeset()).isEqualTo(mapOf("key", "value"));
    }



    @Test
    public void VersionNode_Should_Analyze_Actual_Changeset() {
        SimpleVersion v1 = new Builder()
                .changeset(ImmutableMap.of("id", "id1", "name", "name1"))
                .build();

        // v2.parents == v3.parents, non-conflicting changes from not-a-diff-based versions

        SimpleVersion v2 = new Builder()
                .changeset(ImmutableMap.of("id", "id2", "name", "name1"))
                .parents(v1.revision)
                .build();

        SimpleVersion v3 = new Builder()
                .changeset(ImmutableMap.of("id", "id1", "name", "name2"))
                .parents(v1.revision)
                .build();

        SimpleVersionGraph versionGraph = init(asList(v1, v2, v3));

        VersionNode versionNode = versionGraph.getVersionNode(v2.revision);
        assertThat(versionNode.getChangeset()).isEqualTo(ImmutableMap.of("id", "id2"));
        Version<String, String, String> version = versionNode.getVersion();
        assertThat(version).isNotSameAs(v2);
        assertThat(version.revision).isEqualTo(v2.revision);
        assertThat(version.type).isEqualTo(v2.type);
        assertThat(version.branch).isEqualTo(v2.branch);
        assertThat(version.parentRevisions).isEqualTo(v2.parentRevisions);
        assertThat(version.meta).isEqualTo(v2.meta);
        // VersionNode.getVersion() reflects actual changes
        assertThat(version.changeset).isEqualTo(ImmutableMap.of("id", "id2"));

        versionNode = versionGraph.getVersionNode(v3.revision);
        assertThat(versionNode.getChangeset()).isEqualTo(ImmutableMap.of("name", "name2"));

        Merge<String, String, String> merge = versionGraph.mergeBranches(DEFAULT_BRANCH);
        assertThat(merge.getConflicts().isEmpty()).isTrue();
        assertThat(merge.getProperties()).isEqualTo(ImmutableMap.of("id", "id2", "name", "name2"));
    }

    @Test
    public void at() {
        SimpleVersion v1 = new Builder()
                .branch("branch1")
                .changeset(ImmutableMap.of("key", "value1"))
                .build();
        SimpleVersion v2 = new Builder()
                .branch("branch2")
                .changeset(ImmutableMap.of("key", "value2"))
                .parents(v1.revision)
                .build();

        SimpleVersionGraph versionGraph = init(asList(v1, v2));
        assertThat(versionGraph.getBranches()).isEqualTo(ImmutableSet.of("branch1", "branch2"));
        assertThat(versionGraph.getHead("branch1")).isEqualTo(versionGraph.getVersionNode(v1.revision));
        assertThat(versionGraph.getHead("branch2")).isEqualTo(versionGraph.getVersionNode(v2.revision));

        SimpleVersionGraph at = versionGraph.at(v1.revision);
        assertThat(at).isNotEqualTo(versionGraph);
        assertThat(at.getBranches()).isEqualTo(ImmutableSet.of("branch1"));
        assertThat(at.getHead("branch1")).isEqualTo(versionGraph.getVersionNode(v1.revision));
        assertThat(at.getHead("branch2")).isNull();

        versionGraph = at.atTip();
        assertThat(versionGraph.getBranches()).isEqualTo(ImmutableSet.of("branch1", "branch2"));
        assertThat(versionGraph.getHead("branch1")).isEqualTo(versionGraph.getVersionNode(v1.revision));
        assertThat(versionGraph.getHead("branch2")).isEqualTo(versionGraph.getVersionNode(v2.revision));
    }

    @Test
    public void tip_of_tip_is_same_instance() {
        SimpleVersionGraph graph = init();
        assertThat(graph.atTip()).isSameAs(graph);
    }

    @Test
    public void merge_revisions() {
        SimpleVersion v1 = new Builder()
                .branch("branch1")
                .changeset(ImmutableMap.of("key","value1", "foo","bar"))
                .build();
        SimpleVersion v2 = new Builder()
                .branch("branch2")
                .changeset(ImmutableMap.of("key", "value2", "bar", "foo"))
                .build();

        SimpleVersionGraph versionGraph = init(asList(v1, v2));
        Merge<String, String, String> merge = versionGraph.mergeRevisions(v1.revision, v2.revision);
        assertThat(merge.getProperties()).isEqualTo(ImmutableMap.of("key","value2", "foo","bar", "bar","foo"));
        assertThat(merge.conflicts.entries()).hasSize(1);

        assertThat(merge.getProperties())
                .withFailMessage("Order doesn't matter")
                .isEqualTo(versionGraph.mergeRevisions(v2.revision, v1.revision).getProperties());
    }

    @Test
    public void version_order_is_maintained() {
        SimpleVersion v1 = new Builder()
                .changeset(ImmutableMap.of("key", "value1"))
                .build();
        SimpleVersion v2 = new Builder()
                .changeset(ImmutableMap.of("key", "value2"))
                .build();

        assertThat(v1.revision).isLessThan(v2.revision);

        SimpleVersionGraph versionGraph = init(asList(v2, v1));
        Merge<String, String, String> merge = versionGraph.mergeBranches(DEFAULT_BRANCH);
        // Insertion order doesn't affect merge
        assertThat(merge.getProperties().get("key")).isEqualTo("value2");

        assertThat(versionGraph.getTip().revision).isEqualTo(v1.revision);
    }

    @Test
    public void discard_unnecessary_nulls() {
        SimpleVersion v1 = new Builder()
                .changeset(mapOf("key1", null, "key2", null))
                .build();
        SimpleVersionGraph graph = init(v1);
        assertThat(graph.getTip().getChangeset().isEmpty()).isEqualTo(true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicate_revision_throws_exception() {
        Revision rev = new Revision();
        SimpleVersion v1 = builder(rev).build();
        SimpleVersion v2 = new Builder(rev).build();

        init(asList(v1, v2));
    }

    @Test
    public void is_empty() {
        assertThat(init().isEmpty()).isEqualTo(true);
        assertThat(init(new Builder().build()).isEmpty()).isEqualTo(false);
    }

    @Test
    public void get_heads() {
        Revision r1 = new Revision(),
                r2 = new Revision();
        SimpleVersionGraph graph = init(builder(r1).branch("b1").build(), builder(r2).branch("b2").build());
        assertThat(graph.getHeadRevisions()).isEqualTo(ImmutableSet.of(r1, r2));
        assertThat(copyOf(graph.getHeadRevisions("b1"))).isEqualTo(ImmutableSet.of(r1));
        assertThat(copyOf(graph.getHeadRevisions("b2"))).isEqualTo(ImmutableSet.of(r2));
    }

    @Test
    public void containsAll() {
        SimpleVersionGraph graph = init();
        assertThat(graph.containsAll(new ArrayList<>())).isTrue();

        Revision r1 = new Revision(), r2 = new Revision();
        graph = init(builder(r1).build(), builder(r2).build());
        assertThat(graph.containsAll(asList(r1, r2))).isTrue();
        assertThat(graph.containsAll(asList(r1, new Revision()))).isFalse();
    }

    private void runExpectations(SimpleVersionGraph versionGraph, List<VersionExpectation> expectations) {
        for (VersionExpectation expectation : expectations) {
            Revision revision = expectation.getRevision();
            if (revision != null) {
                versionGraph = versionGraph.at(revision);
            }
            assertGraphExpectations(versionGraph, revision, expectation);
            assertMergeExpectations(versionGraph, revision, expectation);
        }
    }

    private Iterable<SimpleVersion> getVersions(
            List<VersionExpectation> expectations) {
        return filter(transform(expectations, getVersion), notNull());
    }

    private void assertGraphExpectations(SimpleVersionGraph versionGraph, Revision revision, VersionExpectation expectation) {
        if (expectation.expectedHeads != null) {
            Set<Revision> heads = new HashSet<>();
            for (BranchAndRevision leaf : versionGraph.getHeads().keys()) {
                heads.add(leaf.revision);
            }
            assertThat(heads)
                    .withFailMessage(title("heads", revision, expectation))
                    .isEqualTo(expectation.expectedHeads);
        }
    }

    private void assertMergeExpectations(SimpleVersionGraph versionGraph, Revision revision, VersionExpectation expectation) {
        try {
            Merge<String, String, String> merge;
            if (expectation.mergeBranches != null) {
                merge = versionGraph.mergeBranches(expectation.mergeBranches);
            } else {
                merge = versionGraph.mergeRevisions(expectation.mergeRevisions);
            }
            assertThat(merge.getMergeHeads())
                    .withFailMessage(title("mergeHeads", revision, expectation))
                    .isEqualTo(expectation.expectedMergeHeads);

            assertThat(merge.getProperties())
                    .withFailMessage(title("properties", revision, expectation))
                    .isEqualTo(expectation.expectedProperties);

            assertThat(Multimaps.transformValues(merge.conflicts, merge.getVersionPropertyValue))
                    .withFailMessage(title("conflicts", revision, expectation))
                    .isEqualTo(expectation.expectedConflicts);
        } catch (RuntimeException e) {
            throw new AssertionError(title("merge", revision, expectation), e);
        }
    }

    private static String title(String assertLabel, Revision revision, VersionExpectation expectation) {
        return assertLabel + " of " + revision + (expectation.title != null ? ": " + expectation.title : "");
    }

    public static Function<VersionExpectation, SimpleVersion> getVersion = new Function<VersionExpectation, SimpleVersion>() {
        @Override
        public SimpleVersion apply(VersionExpectation input) {
            return input.version;
        }
    };

    public static class VersionExpectation {
        public final String title;
        public final SimpleVersion version;
        public Set<Revision> mergeRevisions = ImmutableSet.of();
        public Iterable<String> mergeBranches;
        public Map<String, String> expectedProperties;
        public Multimap<String, String> expectedConflicts = ImmutableMultimap.of();
        public Set<Revision> expectedMergeHeads;
        public Set<Revision> expectedHeads;
        public VersionExpectation(String title) {
            this(null, title);
        }
        public VersionExpectation(SimpleVersion version) {
            this(version, null);
        }
        public VersionExpectation(SimpleVersion version, String title) {
            this.version = version;
            this.title = title;
            if (version != null) {
                this.mergeRevisions = ImmutableSet.of(version.revision);
            }
            this.expectedMergeHeads = mergeRevisions;
        }
        public Revision getRevision() {
            return version != null ? version.revision : null;
        }
        public VersionExpectation mergeRevisions(Set<Revision> mergeRevisions) {
            this.mergeRevisions = mergeRevisions;
            this.expectedMergeHeads = mergeRevisions;
            return this;
        }
        public VersionExpectation mergeBranches(Iterable<String> mergeBranches) {
            this.mergeBranches = mergeBranches;
            return this;
        }
        public VersionExpectation expectProperties(Map<String, String> expectedProperties) {
            this.expectedProperties = expectedProperties;
            return this;
        }
        public VersionExpectation expectMergeHeads(Set<Revision> expectedRevisions) {
            this.expectedMergeHeads = expectedRevisions;
            return this;
        }
        public VersionExpectation expectAllHeads(Set<Revision> expectedHeads) {
            this.expectedHeads = expectedHeads;
            return this;
        }
        public VersionExpectation expectConflicts(Multimap<String, String> expectedConflicts) {
            this.expectedConflicts = expectedConflicts;
            return this;
        }
    }

    public static Builder version(Revision rev) {
        return new Builder(rev);
    }

    public static VersionExpectation when(Builder builder) {
        return new VersionExpectation(builder.build());
    }
    public static VersionExpectation then(String title) {
        return new VersionExpectation(title);
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... revs) {
        return copyOf(revs);
    }

    public static Map<String, String> mapOf(String... entries) {
        Map<String, String> map = Maps.newHashMap();
        for (int i=0; i+1 < entries.length; i+=2) {
            map.put(entries[i], entries[i+1]);
        }
        return unmodifiableMap(map);
    }

    public static Multimap<String, String> multimapOf(String... entries) {
        Multimap<String, String> map = ArrayListMultimap.create();
        for (int i=0; i+1 < entries.length; i+=2) {
            map.put(entries[i], entries[i+1]);
        }
        return map;
    }

}
