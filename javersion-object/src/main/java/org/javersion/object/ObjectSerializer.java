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
package org.javersion.object;

import static org.javersion.object.TypeMappings.DEFAULT;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.javersion.object.types.ValueType;
import org.javersion.path.PropertyPath;
import org.javersion.path.Schema;

import com.google.common.reflect.TypeToken;

@Immutable
public class ObjectSerializer<O> {

    public final Schema<ValueType> schemaRoot;

    public ObjectSerializer(Class<O> clazz) {
        this.schemaRoot = new DescribeContext(DEFAULT).describeSchema(clazz);
    }

    public ObjectSerializer(TypeToken<O> typeToken) {
        this.schemaRoot = new DescribeContext(DEFAULT).describeSchema(typeToken);
    }

    public ObjectSerializer(Class<O> clazz, TypeMappings typeMappings) {
        this.schemaRoot = new DescribeContext(typeMappings).describeSchema(clazz);
    }

    public ObjectSerializer(TypeToken<O> typeToken, TypeMappings typeMappings) {
        this.schemaRoot = new DescribeContext(typeMappings).describeSchema(typeToken);
    }

    public Map<PropertyPath, Object> toPropertyMap(O object) {
        return new WriteContext(schemaRoot, object).getMap();
    }

    @SuppressWarnings("unchecked")
    public O fromPropertyMap(Map<PropertyPath, Object> properties) {
        return (O) new ReadContext(schemaRoot, properties).getObject();
    }

}
