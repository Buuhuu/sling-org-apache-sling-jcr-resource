/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.jcr.resource.internal;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;

import org.apache.sling.api.resource.PersistableValueMap;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jcr.resource.JcrModifiablePropertyMap;
import org.apache.sling.jcr.resource.JcrPropertyMap;
import org.apache.sling.jcr.resource.JcrResourceUtil;

public class JcrModifiablePropertyMapTest extends JcrPropertyMapTest {

    private String rootPath;

    private Node rootNode;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        rootPath = "/test_" + System.currentTimeMillis();
        rootNode = getSession().getRootNode().addNode(rootPath.substring(1),
            "nt:unstructured");

        final Map<String, Object> values = this.initialSet();
        for(Map.Entry<String, Object> entry : values.entrySet()) {
            JcrResourceUtil.setProperty(rootNode, entry.getKey().toString(), entry.getValue());
        }
        session.save();
    }

    @Override
    protected void tearDown() throws Exception {
        if (rootNode != null) {
            rootNode.remove();
            session.save();
        }

        super.tearDown();
    }

    private Map<String, Object> initialSet() {
        final Map<String, Object> values = new HashMap<String, Object>();
        values.put("string", "test");
        values.put("long", 1L);
        values.put("bool", Boolean.TRUE);
        return values;
    }

    public void testPut()
    throws IOException {
        final PersistableValueMap pvm = new JcrModifiablePropertyMap(this.rootNode);
        assertContains(pvm, initialSet());
        assertNull(pvm.get("something"));

        // now put two values and check set again
        pvm.put("something", "Another value");
        pvm.put("string", "overwrite");

        final Map<String, Object> currentlyStored = this.initialSet();
        currentlyStored.put("something", "Another value");
        currentlyStored.put("string", "overwrite");
        assertContains(pvm, currentlyStored);

        pvm.save();
        assertContains(pvm, currentlyStored);

        final PersistableValueMap pvm2 = new JcrModifiablePropertyMap(this.rootNode);
        assertContains(pvm2, currentlyStored);
    }

    public void testReset()
    throws IOException {
        final PersistableValueMap pvm = new JcrModifiablePropertyMap(this.rootNode);
        assertContains(pvm, initialSet());
        assertNull(pvm.get("something"));

        // now put two values and check set again
        pvm.put("something", "Another value");
        pvm.put("string", "overwrite");

        final Map<String, Object> currentlyStored = this.initialSet();
        currentlyStored.put("something", "Another value");
        currentlyStored.put("string", "overwrite");
        assertContains(pvm, currentlyStored);

        pvm.reset();
        assertContains(pvm, initialSet());

        final PersistableValueMap pvm2 = new JcrModifiablePropertyMap(this.rootNode);
        assertContains(pvm2, initialSet());
    }

    public void testSerializable()
    throws IOException {
        final PersistableValueMap pvm = new JcrModifiablePropertyMap(this.rootNode);
        assertContains(pvm, initialSet());
        assertNull(pvm.get("something"));

        // now put a serializable object
        final List<String> strings = new ArrayList<String>();
        strings.add("a");
        strings.add("b");
        pvm.put("something", strings);

        // check if we get the list again
        @SuppressWarnings("unchecked")
        final List<String> strings2 = (List<String>) pvm.get("something");
        assertEquals(strings, strings2);

        pvm.save();

        final PersistableValueMap pvm2 = new JcrModifiablePropertyMap(this.rootNode);
        // check if we get the list again
        @SuppressWarnings("unchecked")
        final List<String> strings3 = (List<String>) pvm2.get("something", Serializable.class);
        assertEquals(strings, strings3);

    }

    public void testExceptions() {
        final PersistableValueMap pvm = new JcrModifiablePropertyMap(this.rootNode);
        try {
            pvm.put(null, "something");
            fail("Put with null key");
        } catch (NullPointerException iae) {}
        try {
            pvm.put("something", null);
            fail("Put with null value");
        } catch (NullPointerException iae) {}
        try {
            pvm.put("something", pvm);
            fail("Put with non serializable");
        } catch (IllegalArgumentException iae) {}
    }

    protected JcrPropertyMap createPropertyMap(final Node node) {
        return new JcrModifiablePropertyMap(node);
    }

    /**
     * Check that the value map contains all supplied values
     */
    private void assertContains(ValueMap map, Map<String, Object> values) {
        for(Map.Entry<String, Object> entry : values.entrySet()) {
            final Object stored = map.get(entry.getKey());
            assertEquals(values.get(entry.getKey()), stored);
        }
    }
}
