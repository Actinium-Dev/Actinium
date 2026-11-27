package com.gtnewhorizons.angelica.sdlgpu.shader;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.lwjgl.util.spvc.Spv;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.system.MemoryUtil.memAlloc;

/** Adds typed zero stores for the color outputs of a shaderc fragment module. */
final class FragmentOutputInitializer {
    private static final int HEADER_WORDS = 5;
    private static final int ID_BOUND = 3;
    private static final int MAIN_NAME_WORD = 0x6e69616d;

    private FragmentOutputInitializer() {}

    /** Returns the original buffer or a new native buffer owned by the caller. */
    static ByteBuffer initialize(ByteBuffer spirv) {
        final IntBuffer words = spirv.asIntBuffer();
        if (words.limit() < HEADER_WORDS || words.get(0) != Spv.SpvMagicNumber) {
            throw new IllegalArgumentException("Invalid SPIR-V header");
        }

        final IntArrayList entryInterface = new IntArrayList();
        final IntOpenHashSet locations = new IntOpenHashSet();
        final Int2IntOpenHashMap outputPointers = new Int2IntOpenHashMap();
        final Int2IntOpenHashMap outputVariables = new Int2IntOpenHashMap();
        int entryFunction = 0;
        int firstFunction = words.limit();
        for (int offset = HEADER_WORDS; offset < words.limit(); ) {
            final int opcode = words.get(offset) & 0xffff;
            final int length = instructionLength(words, offset);
            if (opcode == Spv.SpvOpFunction) {
                firstFunction = offset;
                break;
            }
            switch (opcode) {
                case Spv.SpvOpEntryPoint -> {
                    if (length >= 5 && words.get(offset + 1) == Spv.SpvExecutionModelFragment
                            && words.get(offset + 3) == MAIN_NAME_WORD && words.get(offset + 4) == 0) {
                        entryFunction = words.get(offset + 2);
                        for (int operand = offset + 5; operand < offset + length; operand++) {
                            entryInterface.add(words.get(operand));
                        }
                    }
                }
                case Spv.SpvOpDecorate -> {
                    if (words.get(offset + 2) == Spv.SpvDecorationLocation) {
                        locations.add(words.get(offset + 1));
                    }
                }
                case Spv.SpvOpTypePointer -> {
                    if (words.get(offset + 2) == Spv.SpvStorageClassOutput) {
                        outputPointers.put(words.get(offset + 1), words.get(offset + 3));
                    }
                }
                case Spv.SpvOpVariable -> {
                    if (length == 4 && words.get(offset + 3) == Spv.SpvStorageClassOutput) {
                        outputVariables.put(words.get(offset + 2), words.get(offset + 1));
                    }
                }
                default -> { }
            }
            offset += length;
        }

        if (entryFunction == 0) throw new IllegalArgumentException("SPIR-V fragment entry point 'main' is missing");

        final Int2IntLinkedOpenHashMap zerosByType = new Int2IntLinkedOpenHashMap();
        final Int2IntLinkedOpenHashMap stores = new Int2IntLinkedOpenHashMap();
        int nextId = words.get(ID_BOUND);
        for (int variable : entryInterface) {
            if (!locations.contains(variable)) continue;
            final int type = outputPointers.get(outputVariables.get(variable));
            if (type == 0) continue;

            int zero = zerosByType.get(type);
            if (zero == 0) {
                zero = nextId++;
                zerosByType.put(type, zero);
            }
            stores.put(variable, zero);
        }
        if (stores.isEmpty()) return spirv;

        final int entryInsertion = entryBlockInsertion(words, firstFunction, entryFunction);
        final int outputWords = words.limit() + 3 * (zerosByType.size() + stores.size());
        final ByteBuffer result = memAlloc(outputWords * Integer.BYTES);
        final IntBuffer output = result.asIntBuffer();
        output.put(words.slice(0, firstFunction));
        output.put(ID_BOUND, nextId);
        for (var zero : zerosByType.int2IntEntrySet()) {
            output.put((3 << 16) | Spv.SpvOpConstantNull).put(zero.getIntKey()).put(zero.getIntValue());
        }
        output.put(words.slice(firstFunction, entryInsertion - firstFunction));
        for (var store : stores.int2IntEntrySet()) {
            output.put((3 << 16) | Spv.SpvOpStore).put(store.getIntKey()).put(store.getIntValue());
        }
        output.put(words.slice(entryInsertion, words.limit() - entryInsertion));
        return result;
    }

    private static int entryBlockInsertion(IntBuffer words, int firstFunction, int entryFunction) {
        boolean inEntryFunction = false;
        for (int offset = firstFunction; offset < words.limit(); ) {
            final int opcode = words.get(offset) & 0xffff;
            final int length = instructionLength(words, offset);
            if (opcode == Spv.SpvOpFunction) {
                inEntryFunction = words.get(offset + 2) == entryFunction;
            } else if (inEntryFunction && opcode == Spv.SpvOpLabel) {
                int insertion = offset + length;
                // Function-scope variables must remain first in the entry block.
                while (insertion < words.limit()) {
                    final int next = words.get(insertion) & 0xffff;
                    if (next != Spv.SpvOpVariable && next != Spv.SpvOpLine && next != Spv.SpvOpNoLine) break;
                    insertion += instructionLength(words, insertion);
                }
                return insertion;
            }
            offset += length;
        }
        throw new IllegalArgumentException("SPIR-V fragment entry block is missing");
    }

    private static int instructionLength(IntBuffer words, int offset) {
        final int length = words.get(offset) >>> 16;
        if (length == 0 || length > words.limit() - offset) {
            throw new IllegalArgumentException("Invalid SPIR-V instruction at word " + offset);
        }
        return length;
    }
}
