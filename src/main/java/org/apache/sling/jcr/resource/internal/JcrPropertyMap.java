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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;

import org.apache.jackrabbit.util.ISO9075;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jcr.resource.JcrResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the value map based on a JCR node.
 * @see JcrModifiablePropertyMap
 */
public class JcrPropertyMap implements ValueMap {

    /** default logger */
    private static Logger LOGGER = LoggerFactory.getLogger(JcrPropertyMap.class);

    /** The underlying node. */
    private final Node node;

    /** A cache for the properties. */
    final Map<String, CacheEntry> cache;

    /** A cache for the values. */
    final Map<String, Object> valueCache;

    /** Has the node been read completly? */
    boolean fullyRead;

    /**
     * Constructor
     * @param node The underlying node.
     */
    public JcrPropertyMap(final Node node) {
        this.node = node;
        this.cache = new LinkedHashMap<String, CacheEntry>();
        this.valueCache = new LinkedHashMap<String, Object>();
        this.fullyRead = false;
    }

    /**
     * Get the node.
     */
    Node getNode() {
        return node;
    }

    // ---------- ValueMap

    /**
     * @see org.apache.sling.api.resource.ValueMap#get(java.lang.String, java.lang.Class)
     */
    @SuppressWarnings("unchecked")
    public <T> T get(final String key, final Class<T> type) {
        if (type == null) {
            return (T) get(key);
        }

        CacheEntry entry = cache.get(key);
        if (entry == null) {
            entry = read(key);
        }
        if ( entry == null ) {
            return null;
        }
        return convertToType(entry, type);
    }

    /**
     * @see org.apache.sling.api.resource.ValueMap#get(java.lang.String, java.lang.Object)
     */
    @SuppressWarnings("unchecked")
    public <T> T get(final String key,final T defaultValue) {
        if (defaultValue == null) {
            return (T) get(key);
        }

        // special handling in case the default value implements one
        // of the interface types supported by the convertToType method
        Class<T> type = (Class<T>) normalizeClass(defaultValue.getClass());

        T value = get(key, type);
        if (value == null) {
            value = defaultValue;
        }

        return value;
    }

    // ---------- Map

    /**
     * @see java.util.Map#get(java.lang.Object)
     */
    public Object get(final Object key) {
        if ( key == null ) {
            return null;
        }
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            entry = read((String)key);
        }
        final Object value = (entry == null ? null : entry.defaultValue);
        return value;
    }

    /**
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    /**
     * @see java.util.Map#containsValue(java.lang.Object)
     */
    public boolean containsValue(Object value) {
        readFully();
        return valueCache.containsValue(value);
    }

    /**
     * @see java.util.Map#isEmpty()
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * @see java.util.Map#size()
     */
    public int size() {
        readFully();
        return cache.size();
    }

    /**
     * @see java.util.Map#entrySet()
     */
    public Set<java.util.Map.Entry<String, Object>> entrySet() {
        readFully();
        return valueCache.entrySet();
    }

    /**
     * @see java.util.Map#keySet()
     */
    public Set<String> keySet() {
        readFully();
        return cache.keySet();
    }

    /**
     * @see java.util.Map#values()
     */
    public Collection<Object> values() {
        readFully();
        return valueCache.values();
    }

    /**
     * Return the path of the current node.
     *
     * @throws IllegalStateException If a repository exception occurs
     */
    public String getPath() {
        try {
            return node.getPath();
        } catch (RepositoryException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- Helpers to access the node's property ------------------------

    CacheEntry read(final String key) {

        // if the node has been completely read, we need not check
        // again, as we certainly will not find the key
        if (fullyRead) {
            return null;
        }

        final String name = ISO9075.encode(key);
        try {
            if (node.hasProperty(name)) {
                final Property prop = node.getProperty(name);
                final CacheEntry entry = new CacheEntry(prop);
                cache.put(key, entry);
                valueCache.put(key, entry.defaultValue);
                return entry;
            }
        } catch (RepositoryException re) {
            // TODO: log !!
        }

        // property not found or some error accessing it
        return null;
    }

    void readFully() {
        if (!fullyRead) {
            try {
                PropertyIterator pi = node.getProperties();
                while (pi.hasNext()) {
                    Property prop = pi.nextProperty();
                    final String name = prop.getName();
                    final String key = ISO9075.decode(name);
                    if (!cache.containsKey(key)) {
                        final CacheEntry entry = new CacheEntry(prop);
                        cache.put(key, entry);
                        valueCache.put(key, entry.defaultValue);
                    }
                }
                fullyRead = true;
            } catch (RepositoryException re) {
                // TODO: log !!
            }
        }
    }

    // ---------- Unsupported Modification methods

    public void clear() {
        throw new UnsupportedOperationException();
    }

    public Object put(String key, Object value) {
        throw new UnsupportedOperationException();
    }

    public void putAll(Map<? extends String, ? extends Object> t) {
        throw new UnsupportedOperationException();
    }

    public Object remove(Object key) {
        throw new UnsupportedOperationException();
    }

    // ---------- Implementation helper

    @SuppressWarnings("unchecked")
    private <T> T convertToType(final CacheEntry entry, Class<T> type) {
        T result = null;

        try {
            final boolean array = type.isArray();

            if (entry.isMulti) {

                if (array) {

                    result = (T) convertToArray(entry,
                        type.getComponentType());

                } else if (entry.values.length > 0) {

                    result = convertToType(entry, -1, entry.values[0], type);

                }

            } else {

                if (array) {

                    result = (T) convertToArray(entry,
                            type.getComponentType());

                } else {

                    result = convertToType(entry, -1, entry.values[0], type);

                }
            }

        } catch (ValueFormatException vfe) {
            LOGGER.info("converToType: Cannot convert value of " + entry.defaultValue
                + " to " + type, vfe);
        } catch (RepositoryException re) {
            LOGGER.info("converToType: Cannot get value of " + entry.defaultValue, re);
        }

        // fall back to nothing
        return result;
    }

    private <T> T[] convertToArray(final CacheEntry entry, Class<T> type)
    throws ValueFormatException, RepositoryException {
        List<T> values = new ArrayList<T>();
        for (int i = 0; i < entry.values.length; i++) {
            T value = convertToType(entry, i, entry.values[i], type);
            if (value != null) {
                values.add(value);
            }
        }

        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(type, values.size());

        return values.toArray(result);
    }

    @SuppressWarnings("unchecked")
    private <T> T convertToType(final CacheEntry entry, int index, Value jcrValue,
            Class<T> type) throws ValueFormatException, RepositoryException {

        if (String.class == type) {
            return (T) jcrValue.getString();

        } else if (Byte.class == type) {
            return (T) Byte.valueOf((byte) jcrValue.getLong());

        } else if (Short.class == type) {
            return (T) Short.valueOf((short) jcrValue.getLong());

        } else if (Integer.class == type) {
            return (T) Integer.valueOf((int) jcrValue.getLong());

        } else if (Long.class == type) {
            if (jcrValue.getType() == PropertyType.BINARY) {
                if (index == -1) {
                    return (T) Long.valueOf(entry.property.getLength());
                }
                return (T) Long.valueOf(entry.property.getLengths()[index]);
            }
            return (T) Long.valueOf(jcrValue.getLong());

        } else if (Float.class == type) {
            return (T) Float.valueOf((float) jcrValue.getDouble());

        } else if (Double.class == type) {
            return (T) Double.valueOf(jcrValue.getDouble());

        } else if (Boolean.class == type) {
            return (T) Boolean.valueOf(jcrValue.getBoolean());

        } else if (Date.class == type) {
            return (T) jcrValue.getDate().getTime();

        } else if (Calendar.class == type) {
            return (T) jcrValue.getDate();

        } else if (Value.class == type) {
            return (T) jcrValue;

        } else if (Property.class == type) {
            return (T) entry.property;
        }

        // fallback in case of unsupported type
        return null;
    }

    private Class<?> normalizeClass(Class<?> type) {
        if (Calendar.class.isAssignableFrom(type)) {
            type = Calendar.class;
        } else if (Date.class.isAssignableFrom(type)) {
            type = Date.class;
        } else if (Value.class.isAssignableFrom(type)) {
            type = Value.class;
        } else if (Property.class.isAssignableFrom(type)) {
            type = Property.class;
        }
        return type;
    }

    static final class CacheEntry {
        public final Property property;
        public final boolean isMulti;
        public final Value[] values;

        public final Object defaultValue;

        public CacheEntry(final Property prop)
        throws RepositoryException {
            this.property = prop;
            if ( prop.getDefinition().isMultiple() ) {
                isMulti = true;
                values = prop.getValues();
            } else {
                isMulti = false;
                values = new Value[] {prop.getValue()};
            }
            this.defaultValue = JcrResourceUtil.toJavaObject(prop);
        }

        public CacheEntry(final Object value, final Node node)
        throws RepositoryException {
            this.property = null;
            this.defaultValue = value;
            if ( value.getClass().isArray() ) {
                this.isMulti = true;
                final Object[] values = (Object[])value;
                this.values = new Value[values.length];
                for(int i=0; i<values.length; i++) {
                    this.values[i] = JcrResourceUtil.createValue(values[i], node.getSession());
                }
            } else {
                this.isMulti = false;
                this.values = new Value[] {JcrResourceUtil.createValue(value, node.getSession())};
            }
        }
    }
}
