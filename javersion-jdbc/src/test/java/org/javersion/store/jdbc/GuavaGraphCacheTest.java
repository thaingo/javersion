package org.javersion.store.jdbc;

import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.querydsl.sql.SQLQueryFactory;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.javersion.core.Revision;
import org.javersion.object.ObjectVersion;
import org.javersion.object.ObjectVersionGraph;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.javersion.core.Revision.NODE;
import static org.javersion.path.PropertyPath.ROOT;
import static org.javersion.store.jdbc.GraphOptions.keepHeadsAndNewest;
import static org.javersion.store.sql.QDocumentVersion.documentVersion;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = PersistenceTestConfiguration.class)
public class GuavaGraphCacheTest {

    @Resource
    DocumentVersionStoreJdbc<String, String, JDocumentVersion<String>> documentStore;

    @Resource
    SQLQueryFactory queryFactory;

    @Resource
    TransactionTemplate transactionTemplate;

    @Test
    public void load_and_refresh() {
        GuavaGraphCache<String, String> cache = newRefreshingCache();

        String docId = randomUUID().toString();

        ObjectVersionGraph<String> versionGraph = cache.load(docId);
        assertThat(versionGraph.isEmpty()).isTrue();

        ObjectVersion<String> version = ObjectVersion.<String>builder()
                .changeset(ImmutableMap.of(ROOT.property("property"), "value"))
                .build();
        documentStore.append(docId, ObjectVersionGraph.init(version).getTip());
        documentStore.publish();

        versionGraph = cache.load(docId);
        assertThat(versionGraph.isEmpty()).isFalse();
        assertThat(versionGraph.getTip().getVersion()).isEqualTo(version);

        version = ObjectVersion.<String>builder()
                .changeset(ImmutableMap.of(ROOT.property("property"), "value2"))
                .build();
        documentStore.append(docId, ObjectVersionGraph.init(version).getTip());
        documentStore.publish();

        versionGraph = cache.load(docId);
        assertThat(versionGraph.getTip().getVersion()).isEqualTo(version);

        cache = newRefreshingCache();
        versionGraph = cache.load(docId);
        assertThat(versionGraph.getTip().getVersion()).isEqualTo(version);

        assertThat(cache.load(docId)).isSameAs(versionGraph);
    }

    @Test
    public void manual_refresh() {
        String docId = randomUUID().toString();
        GuavaGraphCache<String, String> cache = newNonRefreshingCache();


        ObjectVersionGraph<String> versionGraph = cache.load(docId);
        assertThat(versionGraph.isEmpty()).isTrue();

        ObjectVersion<String> version = ObjectVersion.<String>builder()
                .changeset(ImmutableMap.of(ROOT.property("property"), "value"))
                .build();
        documentStore.append(docId, ObjectVersionGraph.init(version).getTip());
        documentStore.publish();

        versionGraph = cache.load(docId);
        assertThat(versionGraph.isEmpty()).isTrue();

        cache.refresh(docId);
        versionGraph = cache.load(docId);
        assertThat(versionGraph.isEmpty()).isFalse();
        assertThat(versionGraph.getTip().getVersion()).isEqualTo(version);
    }

    @Test
    public void clear_cache() throws InterruptedException {
        String docId = randomUUID().toString();
        GuavaGraphCache<String, String> cache = newNonRefreshingCache();

        ObjectVersion<String> version = ObjectVersion.<String>builder().build(); // empty version
        documentStore.append(docId, ObjectVersionGraph.init(version).getTip());
        documentStore.publish();

        assertThat(cache.load(docId).isEmpty()).isEqualTo(false);

        queryFactory.delete(documentVersion).where(documentVersion.revision.eq(version.revision)).execute();

        // Does not hit database
        assertThat(cache.load(docId).isEmpty()).isEqualTo(false);

        cache.evictAll();

        assertThat(cache.load(docId).isEmpty()).isEqualTo(true);
    }

    @Test
    public void return_empty_if_fetch_fails() throws InterruptedException {
        String docId = randomUUID().toString();
        GuavaGraphCache<String, String> cache = newRefreshingCache();

        ObjectVersion<String> version = ObjectVersion.<String>builder().build(); // empty version
        documentStore.append(docId, ObjectVersionGraph.init(version).getTip());

        publish(cache, docId);

        assertThat(cache.load(docId).isEmpty()).isEqualTo(false);

        queryFactory.delete(documentVersion).where(documentVersion.revision.eq(version.revision)).execute();

        Thread.sleep(10);

        assertThat(cache.load(docId).isEmpty()).isEqualTo(true);
    }

    @Test
    public void auto_refresh_only_cached_graphs() {
        final MutableBoolean cacheRefreshed = new MutableBoolean(false);
        DocumentVersionStoreJdbc<String, String, JDocumentVersion<String>> proxyStore = new DocumentVersionStoreJdbc<String, String, JDocumentVersion<String>>(documentStore.options) {
            @Override
            protected FetchResults<String, String> doFetch(String docId, boolean optimized) {
                cacheRefreshed.setTrue();
                throw new RuntimeException("Should not refresh!");
            }
        };

        String docId = randomUUID().toString();
        GuavaGraphCache<String, String> cache = new GuavaGraphCache<String, String>(proxyStore,
                // Non-refreshing cache
                CacheBuilder.<String, ObjectVersionGraph<String>>newBuilder()
                        .maximumSize(8));

        ObjectVersion<String> version = ObjectVersion.<String>builder()
                .changeset(ImmutableMap.of(ROOT.property("property"), "value"))
                .build();
        proxyStore.append(docId, ObjectVersionGraph.init(version).getTip());

        // This should not refresh cache as docId is not cached!
        cache.refresh(docId);
        assertThat(cacheRefreshed.getValue()).isFalse();
    }

    /**
     *  Cache compaction: Keep heads + 1 newest
     *
     *   v1
     *   |
     *   v2
     *  / \
     * v3 |
     * |  v4
     * v5 |
     * |  |
     * v6 |
     *  \/
     *  v7
     */
    @Test
    public void compact_keep_heads_and_one_newest() {
        ObjectVersionGraph<String> graph = ObjectVersionGraph.init();
        GuavaGraphCache<String, String> cache = newRefreshingCache(1, keepHeadsAndNewest(1, 2));

        final String docId = randomUUID().toString();
        final Revision v1 = new Revision(1, NODE),
                v2 = new Revision(2, NODE),
                v3 = new Revision(3, NODE),
                v4 = new Revision(4, NODE),
                v5 = new Revision(5, NODE),
                v6 = new Revision(6, NODE),
                v7 = new Revision(7, NODE);

        graph = graph.commit(ObjectVersion.<String>builder(v1).build());
        documentStore.append(docId, graph.getTip());
        graph = graph.commit(ObjectVersion.<String>builder(v2).parents(v1).build());
        documentStore.append(docId, graph.getTip());
        assertCacheContains(cache, docId, v1, v2);

        // v1 is dropped
        graph = graph.commit(ObjectVersion.<String>builder(v3).parents(v2).build());
        documentStore.append(docId, graph.getTip());
        assertCacheContains(cache, docId, v2, v3);

        // heads v5 and v4 + one newer (v3) and LCA (v2) are kept
        graph = graph.commit(ObjectVersion.<String>builder(v4).parents(v2).build());
        documentStore.append(docId, graph.getTip());
        graph = graph.commit(ObjectVersion.<String>builder(v5).parents(v3).build());
        documentStore.append(docId, graph.getTip());

        assertCacheContains(cache, docId, v2, v3, v4, v5);

        // v3 is dropped
        graph = graph.commit(ObjectVersion.<String>builder(v6).parents(v5).build());
        documentStore.append(docId, graph.getTip());
        assertCacheContains(cache, docId, v2, v4, v5, v6);

        // all other than tip (v7) and second newest are dropped
        graph = graph.commit(ObjectVersion.<String>builder(v7).parents(v6, v4).build());
        documentStore.append(docId, graph.getTip());

        // Optimizing storage doesn't effect cache...
        documentStore.publish();

        transactionTemplate.execute(status -> {
            documentStore.updateBatch(docId)
                    .optimize(documentStore.getOptimizedGraph(docId), v -> v.revision.equals(v7))
                    .execute();
            return null;
        });
        assertCacheContains(cache, docId, v6, v7);

        // ...until reload
        cache.evict(docId);
        assertCacheContains(cache, docId, v7);
    }

    @Test(expected = IllegalArgumentException.class)
    public void keep_predicate_function_is_required() {
        new GraphOptions<String, String>(g -> true, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void when_predicate_is_required() {
        new GraphOptions<String, String>(null, (g) -> v -> true);
    }

    private void assertCacheContains(GuavaGraphCache<String, String> cache, String docId, Revision... revisions) {
        publish(cache, docId);
        ObjectVersionGraph<String> graph = cache.load(docId);
        assertThat(graph.size()).isEqualTo(revisions.length);
        for (Revision revision : revisions) {
            assertThat(graph.contains(revision)).isTrue().overridingErrorMessage("%s not found", revision);
        }
    }

    private void publish(GuavaGraphCache<String, String> cache, String docId) {
        documentStore.publish();
        cache.refresh(docId);
    }

    private GuavaGraphCache<String, String> newRefreshingCache() {
        return newRefreshingCache(1, new GraphOptions<>());
    }

    private GuavaGraphCache<String, String> newRefreshingCache(long refreshAfterNanos, GraphOptions<String, String> graphOptions) {
        return new GuavaGraphCache<>(documentStore,
                CacheBuilder.<String, ObjectVersionGraph<String>>newBuilder()
                        .maximumSize(8)
                        .refreshAfterWrite(refreshAfterNanos, TimeUnit.NANOSECONDS),
                graphOptions
        );
    }

    private GuavaGraphCache<String, String> newNonRefreshingCache() {
        return new GuavaGraphCache<>(documentStore,
                // Non-refreshing cache
                CacheBuilder.<String, ObjectVersionGraph<String>>newBuilder()
                        .maximumSize(8));
    }
}
