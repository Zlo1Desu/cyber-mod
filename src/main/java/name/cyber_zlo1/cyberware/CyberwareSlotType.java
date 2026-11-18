package name.cyber_zlo1.cyberware;

public enum CyberwareSlotType {
	FRONTAL_CORTEX(0, "frontal_cortex", 28, 26),
	OCULAR(1, "ocular", 28, 50),
	CIRCULATORY(2, "circulatory", 28, 74),
	IMMUNE(3, "immune", 28, 98),
	NERVOUS(4, "nervous", 28, 122),
	OPERATING_SYSTEM(5, "operating_system", 176, 26),
	SKELETON(6, "skeleton", 176, 50),
	HANDS(7, "hands", 176, 74),
	ARMS(8, "arms", 176, 98),
	LEGS(9, "legs", 176, 122);

	private final int index;
	private final String id;
	private final int slotX;
	private final int slotY;

	CyberwareSlotType(int index, String id, int slotX, int slotY) {
		this.index = index;
		this.id = id;
		this.slotX = slotX;
		this.slotY = slotY;
	}

	public int getIndex() {
		return index;
	}

	public String getId() {
		return id;
	}

	public String getTranslationKey() {
		return "cyber.slot." + id;
	}

	public int getSlotX() {
		return slotX;
	}

	public int getSlotY() {
		return slotY;
	}
}
