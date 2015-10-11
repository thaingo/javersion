/*
 * Copyright 2015 Samppa Saarela
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
package org.javersion.store.jdbc;

import static com.mysema.query.support.Expressions.constant;
import static com.mysema.query.support.Expressions.predicate;
import static com.mysema.query.types.Ops.EQ;
import static com.mysema.query.types.Ops.IN;
import static java.util.Collections.singleton;
import static org.springframework.transaction.annotation.Isolation.READ_COMMITTED;
import static org.springframework.transaction.annotation.Propagation.REQUIRED;

import java.util.Collection;
import java.util.Map;

import org.javersion.core.Revision;
import org.javersion.core.VersionNode;
import org.javersion.path.PropertyPath;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mysema.query.sql.Configuration;
import com.mysema.query.sql.dml.SQLUpdateClause;
import com.mysema.query.types.OrderSpecifier;
import com.mysema.query.types.Path;
import com.mysema.query.types.Predicate;
import com.mysema.query.types.expr.SimpleExpression;

public class DocumentVersionStoreJdbc<Id, M> extends AbstractVersionStoreJdbc<Id, M, DocumentStoreOptions<Id>> {

    public static void registerTypes(String tablePrefix, Configuration configuration) {
        AbstractVersionStoreJdbc.registerTypes(tablePrefix, configuration);
    }

    @SuppressWarnings("unused")
    protected DocumentVersionStoreJdbc() {
        super();
    }

    public <P extends SimpleExpression<Id> & Path<Id>> DocumentVersionStoreJdbc(DocumentStoreOptions<Id> options) {
        super(options);
    }

    @Transactional(readOnly = false, isolation = READ_COMMITTED, propagation = REQUIRED)
    public void append(Id docId, VersionNode<PropertyPath, Object, M> version) {
        append(docId, singleton(version));
    }

    @Transactional(readOnly = false, isolation = READ_COMMITTED, propagation = REQUIRED)
    public void append(Id docId, Iterable<VersionNode<PropertyPath, Object, M>> versions) {
        ImmutableMultimap.Builder<Id, VersionNode<PropertyPath, Object, M>> builder = ImmutableMultimap.builder();
        append(builder.putAll(docId, versions).build());
    }

    @Transactional(readOnly = false, isolation = READ_COMMITTED, propagation = REQUIRED)
    public void append(Multimap<Id, VersionNode<PropertyPath, Object, M>> versionsByDocId) {
        DocumentUpdateBatch<Id, M> batch = updateBatch();

        for (Id docId : versionsByDocId.keySet()) {
            for (VersionNode<PropertyPath, Object, M> version : versionsByDocId.get(docId)) {
                batch.addVersion(docId, version);
            }
        }

        batch.execute();
    }

    @Override
    protected DocumentUpdateBatch<Id, M> updateBatch() {
        return new DocumentUpdateBatch<>(options);
    }

    @Override
    protected Predicate publicVersionsOf(Id docId) {
        return predicate(EQ, options.version.docId, constant(docId))
                .and(options.version.ordinal.isNotNull());
    }

    @Override
    protected Predicate publicVersionsOf(Collection<Id> docIds) {
        return predicate(IN, constant(docIds))
                .and(options.version.ordinal.isNotNull());
    }

    @Override
    protected OrderSpecifier<?>[] publicVersionsOrderBy() {
        return new OrderSpecifier<?>[] { options.version.ordinal.asc() };
    }

    /**
     * NOTE: publish() needs to be called in a separate transaction from append()!
     * E.g. asynchronously in TransactionSynchronization.afterCommit.
     *
     * Calling publish() in the same transaction with append() severely limits concurrency
     * and might end up in deadlock.
     *
     * @return
     */
    @Transactional(readOnly = false, isolation = READ_COMMITTED, propagation = REQUIRED)
    public Multimap<Id, Revision> publish() {
        // Lock repository with select for update
        long lastOrdinal = getLastOrdinalForUpdate();

        Map<Revision, Id> uncommittedRevisions = findUnpublishedRevisions();
        if (uncommittedRevisions.isEmpty()) {
            return ImmutableMultimap.of();
        }

        Multimap<Id, Revision> publishedDocs = ArrayListMultimap.create();

        SQLUpdateClause versionUpdateBatch = options.queryFactory.update(options.version);

        for (Map.Entry<Revision, Id> entry : uncommittedRevisions.entrySet()) {
            Revision revision = entry.getKey();
            Id docId = entry.getValue();
            publishedDocs.put(docId, revision);
            versionUpdateBatch
                    .set(options.version.ordinal, ++lastOrdinal)
                    .setNull(options.version.localOrdinal)
                    .where(options.version.revision.eq(revision))
                    .addBatch();
        }

        versionUpdateBatch.execute();

        updateLastOrdinal(lastOrdinal);

        afterPublish(publishedDocs);
        return publishedDocs;
    }

    private void updateLastOrdinal(long lastOrdinal) {
        options.queryFactory
                .update(options.repository)
                .set(options.repository.ordinal, lastOrdinal)
                .where(options.repository.id.eq(options.repositoryId))
                .execute();
    }

    protected void afterPublish(Multimap<Id, Revision> publishedDocs) {
        // After publish hook for sub classes to override
    }

    protected Long getLastOrdinalForUpdate() {
        return options.queryFactory
                .from(options.repository)
                .where(options.repository.id.eq(options.repositoryId))
                .forUpdate()
                .singleResult(options.repository.ordinal);
    }

    protected Map<Revision, Id> findUnpublishedRevisions() {
        return options.queryFactory
                .from(options.version)
                .where(options.version.localOrdinal.isNotNull())
                .orderBy(options.version.localOrdinal.asc())
                .map(options.version.revision, options.version.docId);
    }

}
