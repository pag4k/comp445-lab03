package Common;

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

}
