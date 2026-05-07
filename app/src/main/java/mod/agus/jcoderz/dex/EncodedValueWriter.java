/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mod.agus.jcoderz.dex;

import mod.agus.jcoderz.dex.util.ByteOutput;
import static mod.agus.jcoderz.dex.EncodedValueReader.*;

/**
 * Writer for encoded values.
 */
public abstract class EncodedValueWriter {
    protected final ByteOutput out;

    public EncodedValueWriter(ByteOutput out) {
        this.out = out;
    }

    public void transform(EncodedValueReader reader) {
        switch (reader.peek()) {
            case ENCODED_BYTE:
                EncodedValueCodec.writeSignedIntegralValue(out, ENCODED_BYTE, reader.readByte());
                break;
            case ENCODED_SHORT:
                EncodedValueCodec.writeSignedIntegralValue(out, ENCODED_SHORT, reader.readShort());
                break;
            case ENCODED_INT:
                EncodedValueCodec.writeSignedIntegralValue(out, ENCODED_INT, reader.readInt());
                break;
            case ENCODED_LONG:
                EncodedValueCodec.writeSignedIntegralValue(out, ENCODED_LONG, reader.readLong());
                break;
            case ENCODED_CHAR:
                EncodedValueCodec.writeUnsignedIntegralValue(out, ENCODED_CHAR, reader.readChar());
                break;
            case ENCODED_FLOAT:
                // Shift value left 32 so that right-zero-extension works.
                long longBits = ((long) Float.floatToIntBits(reader.readFloat())) << 32;
                EncodedValueCodec.writeRightZeroExtendedValue(out, ENCODED_FLOAT, longBits);
                break;
            case ENCODED_DOUBLE:
                EncodedValueCodec.writeRightZeroExtendedValue(
                        out, ENCODED_DOUBLE, Double.doubleToLongBits(reader.readDouble()));
                break;
            case ENCODED_METHOD_TYPE:
                EncodedValueCodec.writeUnsignedIntegralValue(
                        out, ENCODED_METHOD_TYPE, adjustProto(reader.readMethodType()));
                break;
            case ENCODED_METHOD_HANDLE:
                EncodedValueCodec.writeUnsignedIntegralValue(
                        out,
                        ENCODED_METHOD_HANDLE,
                        adjustMethodHandle(reader.readMethodHandle()));
                break;
            case ENCODED_STRING:
                EncodedValueCodec.writeUnsignedIntegralValue(
                        out, ENCODED_STRING, adjustString(reader.readString()));
                break;
            case ENCODED_TYPE:
                EncodedValueCodec.writeUnsignedIntegralValue(
                        out, ENCODED_TYPE, adjustType(reader.readType()));
                break;
            case ENCODED_FIELD:
                EncodedValueCodec.writeUnsignedIntegralValue(
                        out, ENCODED_FIELD, adjustField(reader.readField()));
                break;
            case ENCODED_ENUM:
                EncodedValueCodec.writeUnsignedIntegralValue(
                        out, ENCODED_ENUM, adjustField(reader.readEnum()));
                break;
            case ENCODED_METHOD:
                EncodedValueCodec.writeUnsignedIntegralValue(
                        out, ENCODED_METHOD, adjustMethod(reader.readMethod()));
                break;
            case ENCODED_ARRAY:
                writeTypeAndArg(ENCODED_ARRAY, 0);
                transformArray(reader);
                break;
            case ENCODED_ANNOTATION:
                writeTypeAndArg(ENCODED_ANNOTATION, 0);
                transformAnnotation(reader);
                break;
            case ENCODED_NULL:
                reader.readNull();
                writeTypeAndArg(ENCODED_NULL, 0);
                break;
            case ENCODED_BOOLEAN:
                boolean value = reader.readBoolean();
                writeTypeAndArg(ENCODED_BOOLEAN, value ? 1 : 0);
                break;
            default:
                throw new DexException("Unexpected type: " + Integer.toHexString(reader.peek()));
        }
    }

    public void transformAnnotation(EncodedValueReader reader) {
        int fieldCount = reader.readAnnotation();
        Leb128.writeUnsignedLeb128(out, adjustType(reader.getAnnotationType()));
        Leb128.writeUnsignedLeb128(out, fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            Leb128.writeUnsignedLeb128(out, adjustString(reader.readAnnotationName()));
            transform(reader);
        }
    }

    public void transformArray(EncodedValueReader reader) {
        int size = reader.readArray();
        Leb128.writeUnsignedLeb128(out, size);
        for (int i = 0; i < size; i++) {
            transform(reader);
        }
    }

    protected void writeTypeAndArg(int type, int arg) {
        out.writeByte((arg << 5) | type);
    }

    protected int adjustString(int stringIndex) {
        return stringIndex;
    }

    protected int adjustType(int typeIndex) {
        return typeIndex;
    }

    protected int adjustProto(int protoIndex) {
        return protoIndex;
    }

    protected int adjustField(int fieldIndex) {
        return fieldIndex;
    }

    protected int adjustMethod(int methodIndex) {
        return methodIndex;
    }

    protected int adjustMethodHandle(int methodHandleIndex) {
        return methodHandleIndex;
    }
}
