/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.iceberg.avro;

import com.google.common.base.Preconditions;
import com.netflix.iceberg.types.TypeUtil;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.io.Encoder;
import org.apache.avro.util.Utf8;
import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ValueWriters {
  private ValueWriters() {
  }

  public static ValueWriter<Void> nulls() {
    return NullWriter.INSTANCE;
  }

  public static ValueWriter<Boolean> booleans() {
    return BooleanWriter.INSTANCE;
  }

  public static ValueWriter<Integer> ints() {
    return IntegerWriter.INSTANCE;
  }

  public static ValueWriter<Long> longs() {
    return LongWriter.INSTANCE;
  }

  public static ValueWriter<Float> floats() {
    return FloatWriter.INSTANCE;
  }

  public static ValueWriter<Double> doubles() {
    return DoubleWriter.INSTANCE;
  }

  public static ValueWriter<String> strings() {
    return StringWriter.INSTANCE;
  }

  public static ValueWriter<Utf8> utf8s() {
    return Utf8Writer.INSTANCE;
  }

  public static ValueWriter<UUID> uuids() {
    return UUIDWriter.INSTANCE;
  }

  public static ValueWriter<byte[]> fixed(int length) {
    return new FixedWriter(length);
  }

  public static ValueWriter<byte[]> bytes() {
    return BytesWriter.INSTANCE;
  }

  public static ValueWriter<BigDecimal> decimal(int precision, int scale) {
    return new DecimalWriter(precision, scale);
  }

  public static <T> ValueWriter<T> option(int nullIndex, ValueWriter<T> writer) {
    return new OptionWriter<>(nullIndex, writer);
  }

  public static <T> ValueWriter<Collection<T>> array(ValueWriter<T> elementWriter) {
    return new CollectionWriter<>(elementWriter);
  }

  public static <K, V> ValueWriter<Map<K, V>> arrayMap(ValueWriter<K> keyWriter,
                                                       ValueWriter<V> valueWriter) {
    return new ArrayMapWriter<>(keyWriter, valueWriter);
  }

  public static <K, V> ValueWriter<Map<K, V>> map(ValueWriter<K> keyWriter,
                                                  ValueWriter<V> valueWriter) {
    return new MapWriter<>(keyWriter, valueWriter);
  }

  public static ValueWriter<IndexedRecord> record(List<ValueWriter<?>> writers) {
    return new RecordWriter(writers);
  }

  private static class NullWriter implements ValueWriter<Void> {
    private static NullWriter INSTANCE = new NullWriter();

    private NullWriter() {
    }

    @Override
    public void write(Void ignored, Encoder encoder) throws IOException {
      encoder.writeNull();
    }
  }

  private static class BooleanWriter implements ValueWriter<Boolean> {
    private static BooleanWriter INSTANCE = new BooleanWriter();

    private BooleanWriter() {
    }

    @Override
    public void write(Boolean bool, Encoder encoder) throws IOException {
      encoder.writeBoolean(bool);
    }
  }

  private static class IntegerWriter implements ValueWriter<Integer> {
    private static IntegerWriter INSTANCE = new IntegerWriter();

    private IntegerWriter() {
    }

    @Override
    public void write(Integer i, Encoder encoder) throws IOException {
      encoder.writeInt(i);
    }
  }

  private static class LongWriter implements ValueWriter<Long> {
    private static LongWriter INSTANCE = new LongWriter();

    private LongWriter() {
    }

    @Override
    public void write(Long l, Encoder encoder) throws IOException {
      encoder.writeLong(l);
    }
  }

  private static class FloatWriter implements ValueWriter<Float> {
    private static FloatWriter INSTANCE = new FloatWriter();

    private FloatWriter() {
    }

    @Override
    public void write(Float f, Encoder encoder) throws IOException {
      encoder.writeFloat(f);
    }
  }

  private static class DoubleWriter implements ValueWriter<Double> {
    private static DoubleWriter INSTANCE = new DoubleWriter();

    private DoubleWriter() {
    }

    @Override
    public void write(Double d, Encoder encoder) throws IOException {
      encoder.writeDouble(d);
    }
  }

  private static class StringWriter implements ValueWriter<String> {
    private static StringWriter INSTANCE = new StringWriter();

    private StringWriter() {
    }

    @Override
    public void write(String s, Encoder encoder) throws IOException {
      // use getBytes because it may return the backing byte array if available.
      // otherwise, it copies to a new byte array, which is still cheaper than Avro
      // calling toString, which incurs encoding costs
      encoder.writeString(new Utf8(s));
    }
  }

  private static class Utf8Writer implements ValueWriter<Utf8> {
    private static Utf8Writer INSTANCE = new Utf8Writer();

    private Utf8Writer() {
    }

    @Override
    public void write(Utf8 s, Encoder encoder) throws IOException {
      encoder.writeString(s);
    }
  }

  private static class UUIDWriter implements ValueWriter<UUID> {
    private static final ThreadLocal<ByteBuffer> BUFFER = ThreadLocal.withInitial(() -> {
      ByteBuffer buffer = ByteBuffer.allocate(16);
      buffer.order(ByteOrder.BIG_ENDIAN);
      return buffer;
    });

    private static UUIDWriter INSTANCE = new UUIDWriter();

    private UUIDWriter() {
    }

    @Override
    public void write(UUID uuid, Encoder encoder) throws IOException {
      // TODO: direct conversion from string to byte buffer
      ByteBuffer buffer = BUFFER.get();
      buffer.rewind();
      buffer.putLong(uuid.getMostSignificantBits());
      buffer.putLong(uuid.getLeastSignificantBits());
      encoder.writeFixed(buffer.array());
    }
  }

  private static class FixedWriter implements ValueWriter<byte[]> {
    private final int length;

    private FixedWriter(int length) {
      this.length = length;
    }

    @Override
    public void write(byte[] bytes, Encoder encoder) throws IOException {
      Preconditions.checkArgument(bytes.length == length,
          "Cannot write byte array of length %s as fixed[%s]", bytes.length, length);
      encoder.writeFixed(bytes);
    }
  }

  private static class BytesWriter implements ValueWriter<byte[]> {
    private static BytesWriter INSTANCE = new BytesWriter();

    private BytesWriter() {
    }

    @Override
    public void write(byte[] bytes, Encoder encoder) throws IOException {
      encoder.writeBytes(bytes);
    }
  }

  private static class DecimalWriter implements ValueWriter<BigDecimal> {
    private final int precision;
    private final int scale;
    private final int length;
    private final ThreadLocal<byte[]> bytes;

    private DecimalWriter(int precision, int scale) {
      this.precision = precision;
      this.scale = scale;
      this.length = TypeUtil.decimalRequriedBytes(precision);
      this.bytes = ThreadLocal.withInitial(() -> new byte[length]);
    }

    @Override
    public void write(BigDecimal decimal, Encoder encoder) throws IOException {
      Preconditions.checkArgument(decimal.scale() == scale,
          "Cannot write value as decimal(%s,%s), wrong scale: %s", precision, scale, decimal);
      Preconditions.checkArgument(decimal.precision() <= precision,
          "Cannot write value as decimal(%s,%s), too large: %s", precision, scale, decimal);

      byte fillByte = (byte) (decimal.signum() < 0 ? 0xFF : 0x00);
      byte[] unscaled = decimal.unscaledValue().toByteArray();
      byte[] buf = bytes.get();
      int offset = length - unscaled.length;

      for (int i = 0; i < length; i += 1) {
        if (i < offset) {
          buf[i] = fillByte;
        } else {
          buf[i] = unscaled[i - offset];
        }
      }

      encoder.writeFixed(buf);
    }
  }

  private static class OptionWriter<T> implements ValueWriter<T> {
    private final int nullIndex;
    private final int valueIndex;
    private final ValueWriter<T> valueWriter;

    private OptionWriter(int nullIndex, ValueWriter<T> valueWriter) {
      this.nullIndex = nullIndex;
      if (nullIndex == 0) {
        this.valueIndex = 1;
      } else if (nullIndex == 1) {
        this.valueIndex = 0;
      } else {
        throw new IllegalArgumentException("Invalid option index: " + nullIndex);
      }
      this.valueWriter = valueWriter;
    }

    @Override
    public void write(T option, Encoder encoder) throws IOException {
      if (option == null) {
        encoder.writeIndex(nullIndex);
      } else {
        encoder.writeIndex(valueIndex);
        valueWriter.write(option, encoder);
      }
    }
  }

  private static class CollectionWriter<T> implements ValueWriter<Collection<T>> {
    private final ValueWriter<T> elementWriter;

    private CollectionWriter(ValueWriter<T> elementWriter) {
      this.elementWriter = elementWriter;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(Collection<T> array, Encoder encoder) throws IOException {
      encoder.writeArrayStart();
      int numElements = array.size();
      encoder.setItemCount(numElements);
      Iterator<T> iter = array.iterator();
      for (int i = 0; i < numElements; i += 1) {
        encoder.startItem();
        elementWriter.write(iter.next(), encoder);
      }
      encoder.writeArrayEnd();
    }
  }

  private static class ArrayMapWriter<K, V> implements ValueWriter<Map<K, V>> {
    private final ValueWriter<K> keyWriter;
    private final ValueWriter<V> valueWriter;

    private ArrayMapWriter(ValueWriter<K> keyWriter, ValueWriter<V> valueWriter) {
      this.keyWriter = keyWriter;
      this.valueWriter = valueWriter;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(Map<K, V> map, Encoder encoder) throws IOException {
      encoder.writeArrayStart();
      int numElements = map.size();
      encoder.setItemCount(numElements);
      Iterator<Map.Entry<K, V>> iter = map.entrySet().iterator();
      for (int i = 0; i < numElements; i += 1) {
        encoder.startItem();
        Map.Entry<K, V> entry = iter.next();
        keyWriter.write(entry.getKey(), encoder);
        valueWriter.write(entry.getValue(), encoder);
      }
      encoder.writeArrayEnd();
    }
  }

  private static class MapWriter<K, V> implements ValueWriter<Map<K, V>> {
    private final ValueWriter<K> keyWriter;
    private final ValueWriter<V> valueWriter;

    private MapWriter(ValueWriter<K> keyWriter, ValueWriter<V> valueWriter) {
      this.keyWriter = keyWriter;
      this.valueWriter = valueWriter;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(Map<K, V> map, Encoder encoder) throws IOException {
      encoder.writeMapStart();
      int numElements = map.size();
      encoder.setItemCount(numElements);
      Iterator<Map.Entry<K, V>> iter = map.entrySet().iterator();
      for (int i = 0; i < numElements; i += 1) {
        encoder.startItem();
        Map.Entry<K, V> entry = iter.next();
        keyWriter.write(entry.getKey(), encoder);
        valueWriter.write(entry.getValue(), encoder);
      }
      encoder.writeMapEnd();
    }
  }

  static class RecordWriter implements ValueWriter<IndexedRecord> {
    final ValueWriter<Object>[] writers;

    @SuppressWarnings("unchecked")
    private RecordWriter(List<ValueWriter<?>> writers) {
      this.writers = (ValueWriter<Object>[]) Array.newInstance(ValueWriter.class, writers.size());
      for (int i = 0; i < this.writers.length; i += 1) {
        this.writers[i] = (ValueWriter<Object>) writers.get(i);
      }
    }

    @Override
    public void write(IndexedRecord row, Encoder encoder) throws IOException {
      for (int i = 0; i < writers.length; i += 1) {
        writers[i].write(row.get(i), encoder);
      }
    }
  }
}