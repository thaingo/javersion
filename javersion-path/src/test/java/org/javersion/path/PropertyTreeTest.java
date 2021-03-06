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
package org.javersion.path;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.javersion.path.NodeId.index;
import static org.javersion.path.NodeId.property;
import static org.javersion.path.PropertyPath.ROOT;
import static org.javersion.path.PropertyPath.parse;
import static org.javersion.path.PropertyPathTest.children;
import static org.javersion.path.PropertyPathTest.children_0;
import static org.javersion.path.PropertyPathTest.children_0_name;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class PropertyTreeTest {

    @Test
    public void Single_Path() {
        PropertyTree tree = PropertyTree.build(children_0_name());
        assertPropertyTree(tree, 1);

        tree = assertPropertyTree(tree, "children", 1);
        tree = assertPropertyTree(tree, 0, 1);
        tree = assertPropertyTree(tree, "name", 0);
    }

    private PropertyTree assertPropertyTree(PropertyTree tree, String property, int expectedChildren) {
        return assertPropertyTree(tree, property(property), expectedChildren);
    }

    private PropertyTree assertPropertyTree(PropertyTree tree, int index, int expectedChildren) {
        return assertPropertyTree(tree, index(index), expectedChildren);
    }

    private PropertyTree assertPropertyTree(PropertyTree tree, NodeId node, int expectedChildren) {
        tree = tree.get(node);
        assertPropertyTree(tree, expectedChildren);
        return tree;
    }

    private void assertPropertyTree(PropertyTree tree, int expectedChildren) {
        assertThat(tree, notNullValue());
        assertThat(tree.getChildrenMap().size(), equalTo(expectedChildren));
    }

    @Test
    public void No_Paths() {
        PropertyTree tree = PropertyTree.build();
        assertThat(tree, nullValue());

    }

    @Test
    public void Paths() {
        PropertyTree root = PropertyTree.build(
                children_0_name(),
                children_0(),
                ROOT.property("name"),
                children.index(123));

        assertPropertyTree(root, 2);

        assertPropertyTree(root, "name", 0);

        PropertyTree children = assertPropertyTree(root, "children", 2);

        PropertyTree tree = assertPropertyTree(children, 0, 1);
        assertPropertyTree(tree, "name", 0);

        assertPropertyTree(children, 123, 0);
    }

    @Test
    public void get_node() {
        NodeId list = property("list");

        PropertyPath path = parse("list[0][\"key\"]");
        PropertyTree tree = PropertyTree.build(path);
        assertThat(tree.getNodeId(), equalTo(ROOT.getNodeId()));

        assertThat(tree.get(list).hasChildren(), equalTo(true));
        assertThat(tree.getChildren(), hasSize(1));

        tree = tree.get(path);
        assertThat(tree.hasChildren(), equalTo(false));
    }

    @Test
    public void to_string() {
        PropertyPath path = children_0_name();
        PropertyTree tree = PropertyTree.build(path);
        assertThat(tree.toString(), equalTo(ROOT.toString()));
        assertThat(tree.get(path).toString(), equalTo(path.toString()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void not_found() {
        PropertyPath path = children_0_name();
        PropertyTree.build(path).get(children.index(123));
    }
}
