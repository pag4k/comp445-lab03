package Common;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ByteUtils {

	public static byte[] ToPrimitives(Byte[] ByteObjects) {
		byte[] BytePrimitives = new byte[ByteObjects.length];

		for (int i = 0; i < ByteObjects.length; i++) {
			BytePrimitives[i] = ByteObjects[i];
		}

		return BytePrimitives;
	}

	public static Byte[] ToObjects(byte[] BytePrimitives) {
		Byte[] ByteObjects = new Byte[BytePrimitives.length];

		int i = 0;
		for (byte Byte : BytePrimitives)
			ByteObjects[i++] = Byte;

		return ByteObjects;
	}

	public static Boolean ExtendToSize(List<Byte> InList, int Size) {
		if (InList.size() > Size) {
			return false;
		}
		List<Byte> AddedList = Collections.nCopies(Size - InList.size(), (byte) 0);
		InList.addAll(AddedList);
		return true;
	}

	public static void AppendTo(List<Byte> InList, byte[] Array) {
		InList.addAll(Arrays.asList(ToObjects(Array)));
	}

}
