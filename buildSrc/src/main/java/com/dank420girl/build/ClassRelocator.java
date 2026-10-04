package com.dank420girl.build;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Moves classes from one package to another by rewriting the constant pool of compiled class files.
 * <p>
 * Every class name, descriptor and generic signature inside a class file is stored as a
 * CONSTANT_Utf8 entry, and nothing in a class file points into the constant pool by byte offset,
 * so replacing those strings is all that is needed. This keeps the build free of third-party
 * plugins such as Shadow.
 */
final class ClassRelocator {
    private final String fromInternal;
    private final String toInternal;
    private final String fromDotted;
    private final String toDotted;

    ClassRelocator(String fromPackage, String toPackage) {
        this.fromDotted = fromPackage.endsWith(".") ? fromPackage : fromPackage + ".";
        this.toDotted = toPackage.endsWith(".") ? toPackage : toPackage + ".";
        this.fromInternal = fromDotted.replace('.', '/');
        this.toInternal = toDotted.replace('.', '/');
    }

    /** Returns the new path of a jar entry, e.g. com/a/common/X.class becomes com/a/plugin/common/X.class. */
    String relocatePath(String path) {
        return path.startsWith(fromInternal) ? toInternal + path.substring(fromInternal.length()) : path;
    }

    String relocateString(String value) {
        if (value.indexOf(fromInternal) < 0 && value.indexOf(fromDotted) < 0) {
            return value;
        }
        return value.replace(fromInternal, toInternal).replace(fromDotted, toDotted);
    }

    byte[] relocateClass(byte[] classFile) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(classFile));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(classFile.length + 256);
        DataOutputStream out = new DataOutputStream(bytes);

        int magic = in.readInt();
        if (magic != 0xCAFEBABE) {
            throw new IOException("Not a class file");
        }
        out.writeInt(magic);
        out.writeShort(in.readUnsignedShort()); // minor
        out.writeShort(in.readUnsignedShort()); // major
        int count = in.readUnsignedShort();
        out.writeShort(count);

        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            out.writeByte(tag);
            switch (tag) {
                case 1: // Utf8
                    out.writeUTF(relocateString(in.readUTF()));
                    break;
                case 3: // Integer
                case 4: // Float
                    out.writeInt(in.readInt());
                    break;
                case 5: // Long
                case 6: // Double
                    out.writeLong(in.readLong());
                    i++; // takes two slots
                    break;
                case 7: // Class
                case 8: // String
                case 16: // MethodType
                case 19: // Module
                case 20: // Package
                    out.writeShort(in.readUnsignedShort());
                    break;
                case 9: // Fieldref
                case 10: // Methodref
                case 11: // InterfaceMethodref
                case 12: // NameAndType
                case 17: // Dynamic
                case 18: // InvokeDynamic
                    out.writeShort(in.readUnsignedShort());
                    out.writeShort(in.readUnsignedShort());
                    break;
                case 15: // MethodHandle
                    out.writeByte(in.readUnsignedByte());
                    out.writeShort(in.readUnsignedShort());
                    break;
                default:
                    throw new IOException("Unknown constant pool tag " + tag + " at index " + i);
            }
        }

        // Everything after the constant pool only refers to it by index: copy it unchanged.
        byte[] rest = new byte[in.available()];
        in.readFully(rest);
        out.write(rest);
        out.flush();
        return bytes.toByteArray();
    }
}
