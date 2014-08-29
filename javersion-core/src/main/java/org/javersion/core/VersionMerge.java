/*
 * Copyright 2014 Samppa Saarela
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

import java.util.Set;

public class VersionMerge<K, V> extends Merge<K, V> {
    
    private Set<Long> heads;
    
    public <T extends Version<K, V>> VersionMerge(Iterable<VersionNode<K, V, T>> nodes) {
        super(new MergeBuilder<K, V>(toMergeNodes(nodes)));
    }

    @Override
    public Set<Long> getMergeHeads() {
        return heads;
    }

    @Override
    protected void setMergeHeads(Set<Long> heads) {
        this.heads = heads;
    }

}
