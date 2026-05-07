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

package mod.agus.jcoderz.dx.merge;

import mod.agus.jcoderz.dex.Annotation;
import mod.agus.jcoderz.dex.CallSiteId;
import mod.agus.jcoderz.dex.ClassDef;
import mod.agus.jcoderz.dex.Dex;
import mod.agus.jcoderz.dex.EncodedValue;
import mod.agus.jcoderz.dex.EncodedValueReader;
import static mod.agus.jcoderz.dex.EncodedValueReader.ENCODED_ARRAY;
import mod.agus.jcoderz.dex.EncodedValueWriter;
import mod.agus.jcoderz.dex.FieldId;
import mod.agus.jcoderz.dex.Leb128;
import mod.agus.jcoderz.dex.MethodHandle;
import mod.agus.jcoderz.dex.MethodId;
import mod.agus.jcoderz.dex.ProtoId;
import mod.agus.jcoderz.dex.TableOfContents;
import mod.agus.jcoderz.dex.TypeList;
import mod.agus.jcoderz.dex.util.ByteOutput;
import mod.agus.jcoderz.dx.util.ByteArrayAnnotatedOutput;

import java.util.HashMap;

/**
 * Maps the index offsets from one dex file to those in another. For example, if
 * you have string #5 in the old dex file, its position in the new dex file is
 * {@code strings[5]}.
 */
public final class IndexMap {
    private final Dex target;
    public final int[] stringIds;
    public final short[] typeIds;
    public final short[] protoIds;
    public final short[] fieldIds;
    public final short[] methodIds;
    public final int[] callSiteIds;
    public final HashMap<Integer, Integer> methodHandleIds;
    private final HashMap<Integer, Integer> typeListOffsets;
    private final HashMap<Integer, Integer> annotationOffsets;
    private final HashMap<Integer, Integer> annotationSetOffsets;
    private final HashMap<Integer, Integer> annotationSetRefListOffsets;
    private final HashMap<Integer, Integer> annotationDirectoryOffsets;
    private final HashMap<Integer, Integer> encodedArrayValueOffset;

    public IndexMap(Dex target, TableOfContents tableOfContents) {
        this.target = target;
        this.stringIds = new int[tableOfContents.stringIds.size];
        this.typeIds = new short[tableOfContents.typeIds.size];
        this.protoIds = new short[tableOfContents.protoIds.size];
        this.fieldIds = new short[tableOfContents.fieldIds.size];
        this.methodIds = new short[tableOfContents.methodIds.size];
        this.callSiteIds = new int[tableOfContents.callSiteIds.size];
        this.methodHandleIds = new HashMap<Integer, Integer>();
        this.typeListOffsets = new HashMap<Integer, Integer>();
        this.annotationOffsets = new HashMap<Integer, Integer>();
        this.annotationSetOffsets = new HashMap<Integer, Integer>();
        this.annotationSetRefListOffsets = new HashMap<Integer, Integer>();
        this.annotationDirectoryOffsets = new HashMap<Integer, Integer>();
        this.encodedArrayValueOffset = new HashMap<Integer, Integer>();

        /*
         * A type list, annotation set, annotation directory, or static value at
         * offset 0 is always empty. Always map offset 0 to 0.
         */
        this.typeListOffsets.put(0, 0);
        this.annotationSetOffsets.put(0, 0);
        this.annotationDirectoryOffsets.put(0, 0);
        this.encodedArrayValueOffset.put(0, 0);
    }

    public void putTypeListOffset(int oldOffset, int newOffset) {
        if (oldOffset <= 0 || newOffset <= 0) {
            throw new IllegalArgumentException();
        }
        typeListOffsets.put(oldOffset, newOffset);
    }

    public void putAnnotationOffset(int oldOffset, int newOffset) {
        if (oldOffset <= 0 || newOffset <= 0) {
            throw new IllegalArgumentException();
        }
        annotationOffsets.put(oldOffset, newOffset);
    }

    public void putAnnotationSetOffset(int oldOffset, int newOffset) {
        if (oldOffset <= 0 || newOffset <= 0) {
            throw new IllegalArgumentException();
        }
        annotationSetOffsets.put(oldOffset, newOffset);
    }

    public void putAnnotationSetRefListOffset(int oldOffset, int newOffset) {
        if (oldOffset <= 0 || newOffset <= 0) {
            throw new IllegalArgumentException();
        }
        annotationSetRefListOffsets.put(oldOffset, newOffset);
    }

    public void putAnnotationDirectoryOffset(int oldOffset, int newOffset) {
        if (oldOffset <= 0 || newOffset <= 0) {
            throw new IllegalArgumentException();
        }
        annotationDirectoryOffsets.put(oldOffset, newOffset);
    }

    public void putEncodedArrayValueOffset(int oldOffset, int newOffset) {
        if (oldOffset <= 0 || newOffset <= 0) {
            throw new IllegalArgumentException();
        }
        encodedArrayValueOffset.put(oldOffset, newOffset);
    }

    public int adjustString(int stringIndex) {
        return stringIndex == ClassDef.NO_INDEX ? ClassDef.NO_INDEX : stringIds[stringIndex];
    }

    public int adjustType(int typeIndex) {
        return (typeIndex == ClassDef.NO_INDEX) ? ClassDef.NO_INDEX : (typeIds[typeIndex] & 0xffff);
    }

    public TypeList adjustTypeList(TypeList typeList) {
        if (typeList == TypeList.EMPTY) {
            return typeList;
        }
        short[] types = typeList.getTypes().clone();
        for (int i = 0; i < types.length; i++) {
            types[i] = (short) adjustType(types[i]);
        }
        return new TypeList(target, types);
    }

    public int adjustProto(int protoIndex) {
        return protoIds[protoIndex] & 0xffff;
    }

    public int adjustField(int fieldIndex) {
        return fieldIds[fieldIndex] & 0xffff;
    }

    public int adjustMethod(int methodIndex) {
        return methodIds[methodIndex] & 0xffff;
    }

    public int adjustTypeListOffset(int typeListOffset) {
        return typeListOffsets.get(typeListOffset);
    }

    public int adjustAnnotation(int annotationOffset) {
        return annotationOffsets.get(annotationOffset);
    }

    public int adjustAnnotationSet(int annotationSetOffset) {
        return annotationSetOffsets.get(annotationSetOffset);
    }

    public int adjustAnnotationSetRefList(int annotationSetRefListOffset) {
        return annotationSetRefListOffsets.get(annotationSetRefListOffset);
    }

    public int adjustAnnotationDirectory(int annotationDirectoryOffset) {
        return annotationDirectoryOffsets.get(annotationDirectoryOffset);
    }

    public int adjustEncodedArray(int encodedArrayAttribute) {
        return encodedArrayValueOffset.get(encodedArrayAttribute);
    }

    public int adjustCallSite(int callSiteIndex) {
        return callSiteIds[callSiteIndex];
    }

    public int adjustMethodHandle(int methodHandleIndex) {
        return methodHandleIds.get(methodHandleIndex);
    }

    public MethodId adjust(MethodId methodId) {
        return new MethodId(target,
                adjustType(methodId.getDeclaringClassIndex()),
                adjustProto(methodId.getProtoIndex()),
                adjustString(methodId.getNameIndex()));
    }

    public CallSiteId adjust(CallSiteId callSiteId) {
        return new CallSiteId(target, adjustEncodedArray(callSiteId.getCallSiteOffset()));
    }

    public MethodHandle adjust(MethodHandle methodHandle) {
        return new MethodHandle(
                target,
                methodHandle.getMethodHandleType(),
                methodHandle.getUnused1(),
                methodHandle.getMethodHandleType().isField()
                        ? adjustField(methodHandle.getFieldOrMethodId())
                        : adjustMethod(methodHandle.getFieldOrMethodId()),
                methodHandle.getUnused2());
    }

    public FieldId adjust(FieldId fieldId) {
        return new FieldId(target,
                adjustType(fieldId.getDeclaringClassIndex()),
                adjustType(fieldId.getTypeIndex()),
                adjustString(fieldId.getNameIndex()));

    }

    public ProtoId adjust(ProtoId protoId) {
        return new ProtoId(target,
                adjustString(protoId.getShortyIndex()),
                adjustType(protoId.getReturnTypeIndex()),
                adjustTypeListOffset(protoId.getParametersOffset()));
    }

    public ClassDef adjust(ClassDef classDef) {
        return new ClassDef(target, classDef.getOffset(), adjustType(classDef.getTypeIndex()),
                classDef.getAccessFlags(), adjustType(classDef.getSupertypeIndex()),
                adjustTypeListOffset(classDef.getInterfacesOffset()), classDef.getSourceFileIndex(),
                classDef.getAnnotationsOffset(), classDef.getClassDataOffset(),
                classDef.getStaticValuesOffset());
    }

    public SortableType adjust(SortableType sortableType) {
        return new SortableType(sortableType.getDex(),
                sortableType.getIndexMap(), adjust(sortableType.getClassDef()));
    }

    public EncodedValue adjustEncodedValue(EncodedValue encodedValue) {
        mod.agus.jcoderz.dx.util.ByteArrayAnnotatedOutput out = new mod.agus.jcoderz.dx.util.ByteArrayAnnotatedOutput(32);
        new EncodedValueTransformer(out).transform(new EncodedValueReader(encodedValue));
        return new EncodedValue(out.toByteArray());
    }

    public EncodedValue adjustEncodedArray(EncodedValue encodedArray) {
        mod.agus.jcoderz.dx.util.ByteArrayAnnotatedOutput out = new mod.agus.jcoderz.dx.util.ByteArrayAnnotatedOutput(32);
        new EncodedValueTransformer(out).transformArray(
                new EncodedValueReader(encodedArray, ENCODED_ARRAY));
        return new EncodedValue(out.toByteArray());
    }

    public Annotation adjust(Annotation annotation) {
        mod.agus.jcoderz.dx.util.ByteArrayAnnotatedOutput out = new ByteArrayAnnotatedOutput(32);
        new EncodedValueTransformer(out).transformAnnotation(
                annotation.getReader());
        return new Annotation(target, annotation.getVisibility(),
                new EncodedValue(out.toByteArray()));
    }

    /**
     * Adjust an encoded value or array.
     */
    private final class EncodedValueTransformer extends EncodedValueWriter {
        public EncodedValueTransformer(ByteOutput out) {
            super(out);
        }

        @Override
        protected int adjustString(int stringIndex) {
            return IndexMap.this.adjustString(stringIndex);
        }

        @Override
        protected int adjustType(int typeIndex) {
            return IndexMap.this.adjustType(typeIndex);
        }

        @Override
        protected int adjustProto(int protoIndex) {
            return IndexMap.this.adjustProto(protoIndex);
        }

        @Override
        protected int adjustField(int fieldIndex) {
            return IndexMap.this.adjustField(fieldIndex);
        }

        @Override
        protected int adjustMethod(int methodIndex) {
            return IndexMap.this.adjustMethod(methodIndex);
        }

        @Override
        protected int adjustMethodHandle(int methodHandleIndex) {
            return IndexMap.this.adjustMethodHandle(methodHandleIndex);
        }
    }
}
