package org.javersion.store.sql;

import static com.mysema.query.types.PathMetadataFactory.*;

import com.mysema.query.types.path.*;

import com.mysema.query.types.PathMetadata;
import javax.annotation.Generated;
import com.mysema.query.types.Path;

import com.mysema.query.sql.ColumnMetadata;
import java.sql.Types;




/**
 * QRepository is a Querydsl query type for QRepository
 */
@Generated("com.mysema.query.sql.codegen.MetaDataSerializer")
public class QRepository extends com.mysema.query.sql.RelationalPathBase<QRepository> {

    private static final long serialVersionUID = -1904416297;

    public static final QRepository repository = new QRepository("REPOSITORY");

    public final StringPath id = createString("id");

    public final com.mysema.query.sql.PrimaryKey<QRepository> constraint9 = createPrimaryKey(id);

    public QRepository(String variable) {
        super(QRepository.class, forVariable(variable), "PUBLIC", "REPOSITORY");
        addMetadata();
    }

    public QRepository(String variable, String schema, String table) {
        super(QRepository.class, forVariable(variable), schema, table);
        addMetadata();
    }

    public QRepository(Path<? extends QRepository> path) {
        super(path.getType(), path.getMetadata(), "PUBLIC", "REPOSITORY");
        addMetadata();
    }

    public QRepository(PathMetadata<?> metadata) {
        super(QRepository.class, metadata, "PUBLIC", "REPOSITORY");
        addMetadata();
    }

    public void addMetadata() {
        addMetadata(id, ColumnMetadata.named("ID").withIndex(1).ofType(Types.VARCHAR).withSize(32).notNull());
    }

}

