package arsenic.event.impl;

import arsenic.event.types.CancellableEvent;

/**
 * Posted at the head of EntityPlayerSP.onUpdate, after {@link EventTick}. Cancelling it drops the local player's whole
 * tick: no movement, no movement packet, nothing else the player ticks. {@link EventTick.Post} still follows.
 */
public class EventDropTick extends CancellableEvent {}
