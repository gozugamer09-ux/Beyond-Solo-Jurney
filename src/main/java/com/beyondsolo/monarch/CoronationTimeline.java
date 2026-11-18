package com.beyondsolo.monarch;

/**
 * When the beats of the coronation fall, in ticks from its start, shared by the server's light show in the hall
 * ({@link ThroneTrial}) and the client's overlay ({@code CoronationOverlay}) so the two stay in step.
 */
public final class CoronationTimeline {
    /** The braziers flare one by one, and light gathers on the throne, until here. */
    public static final int GATHER = 70;
    /** The crown takes shape: the burst, the beam, the title. */
    public static final int LOCK = 90;
    /** The card: what the crown rules and the powers it brings. */
    public static final int CARD = 150;
    /** The end: the hunter is sent home, crowned. */
    public static final int FINISH = 300;

    private CoronationTimeline() {}
}
