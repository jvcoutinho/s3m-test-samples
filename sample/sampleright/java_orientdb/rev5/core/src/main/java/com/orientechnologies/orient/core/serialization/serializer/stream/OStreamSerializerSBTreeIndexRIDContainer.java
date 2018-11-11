/*
 * Copyright 2010-2012 Luca Garulli (l.garulli--at--orientechnologies.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.core.serialization.serializer.stream;

import com.orientechnologies.common.directmemory.ODirectMemoryPointer;
import com.orientechnologies.common.serialization.types.OBinarySerializer;
import com.orientechnologies.common.serialization.types.OBinaryTypeSerializer;
import com.orientechnologies.orient.core.db.record.ridset.sbtree.OSBTreeRidBag;
import com.orientechnologies.orient.core.db.record.ridset.sbtree.OSBTreeIndexRIDContainer;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.serialization.OBinaryProtocol;
import com.orientechnologies.orient.core.serialization.serializer.record.ORecordSerializerFactory;
import com.orientechnologies.orient.core.serialization.serializer.record.string.ORecordSerializerSchemaAware2CSV;

import java.io.IOException;

public class OStreamSerializerSBTreeIndexRIDContainer implements OStreamSerializer, OBinarySerializer<OSBTreeIndexRIDContainer> {
  public static final String                                   NAME     = "ic";
  public static final OStreamSerializerSBTreeIndexRIDContainer INSTANCE = new OStreamSerializerSBTreeIndexRIDContainer();
  private static final ORecordSerializerSchemaAware2CSV        FORMAT   = (ORecordSerializerSchemaAware2CSV) ORecordSerializerFactory
                                                                            .instance().getFormat(
                                                                                ORecordSerializerSchemaAware2CSV.NAME);

  public static final byte                                     ID       = 20;

  public Object fromStream(final byte[] iStream) throws IOException {
    if (iStream == null)
      return null;

    final String s = OBinaryProtocol.bytes2string(iStream);

    return FORMAT.embeddedCollectionFromStream(null, OType.EMBEDDEDSET, null, OType.LINK, s);
  }

  public byte[] toStream(final Object iObject) throws IOException {
    if (iObject == null)
      return null;

    return ((OSBTreeRidBag) iObject).toStream();
  }

  public String getName() {
    return NAME;
  }

  @Override
  public int getObjectSize(OSBTreeIndexRIDContainer object, Object... hints) {
    final byte[] serializedSet = object.toStream();
    return OBinaryTypeSerializer.INSTANCE.getObjectSize(serializedSet);
  }

  @Override
  public int getObjectSize(byte[] stream, int startPosition) {
    return OBinaryTypeSerializer.INSTANCE.getObjectSize(stream, startPosition);
  }

  @Override
  public void serialize(OSBTreeIndexRIDContainer object, byte[] stream, int startPosition, Object... hints) {
    final byte[] serializedSet = object.toStream();
    OBinaryTypeSerializer.INSTANCE.serialize(serializedSet, stream, startPosition);
  }

  @Override
  public OSBTreeIndexRIDContainer deserialize(byte[] stream, int startPosition) {
    final byte[] serializedSet = OBinaryTypeSerializer.INSTANCE.deserialize(stream, startPosition);

    final String s = OBinaryProtocol.bytes2string(serializedSet);

    if (s.startsWith("<#@")) {
      final OSBTreeIndexRIDContainer set = OSBTreeIndexRIDContainer.fromStream(s);
      return set;
    }

    return (OSBTreeIndexRIDContainer) FORMAT.embeddedCollectionFromStream(null, OType.EMBEDDEDSET, null, OType.LINK, s);
  }

  @Override
  public byte getId() {
    return ID;
  }

  @Override
  public boolean isFixedLength() {
    return false;
  }

  @Override
  public int getFixedLength() {
    return 0;
  }

  @Override
  public void serializeNative(OSBTreeIndexRIDContainer object, byte[] stream, int startPosition, Object... hints) {
    final byte[] serializedSet = object.toStream();
    OBinaryTypeSerializer.INSTANCE.serializeNative(serializedSet, stream, startPosition);

  }

  @Override
  public OSBTreeIndexRIDContainer deserializeNative(byte[] stream, int startPosition) {
    final byte[] serializedSet = OBinaryTypeSerializer.INSTANCE.deserializeNative(stream, startPosition);

    final String s = OBinaryProtocol.bytes2string(serializedSet);

    if (s.startsWith("<#@")) {
      final OSBTreeIndexRIDContainer set = OSBTreeIndexRIDContainer.fromStream(s);
      return set;
    }

    return (OSBTreeIndexRIDContainer) FORMAT.embeddedCollectionFromStream(null, OType.EMBEDDEDSET, null, OType.LINK, s);
  }

  @Override
  public int getObjectSizeNative(byte[] stream, int startPosition) {
    return OBinaryTypeSerializer.INSTANCE.getObjectSizeNative(stream, startPosition);
  }

  @Override
  public void serializeInDirectMemory(OSBTreeIndexRIDContainer object, ODirectMemoryPointer pointer, long offset, Object... hints) {
    final byte[] serializedSet = object.toStream();
    OBinaryTypeSerializer.INSTANCE.serializeInDirectMemory(serializedSet, pointer, offset);
  }

  @Override
  public OSBTreeIndexRIDContainer deserializeFromDirectMemory(ODirectMemoryPointer pointer, long offset) {
    final byte[] serializedSet = OBinaryTypeSerializer.INSTANCE.deserializeFromDirectMemory(pointer, offset);

    final String s = OBinaryProtocol.bytes2string(serializedSet);

    if (s.startsWith("<#@")) {
      final OSBTreeIndexRIDContainer set = OSBTreeIndexRIDContainer.fromStream(s);
      return set;
    }

    return (OSBTreeIndexRIDContainer) FORMAT.embeddedCollectionFromStream(null, OType.EMBEDDEDSET, null, OType.LINK, s);
  }

  @Override
  public int getObjectSizeInDirectMemory(ODirectMemoryPointer pointer, long offset) {
    return OBinaryTypeSerializer.INSTANCE.getObjectSizeInDirectMemory(pointer, offset);
  }

  @Override
  public OSBTreeIndexRIDContainer preprocess(OSBTreeIndexRIDContainer value, Object... hints) {
    return value;
  }
}
