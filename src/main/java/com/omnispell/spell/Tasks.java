package com.omnispell.spell;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Runs small jobs a few ticks later on the server thread (delayed blasts, repeated pushes, clean-up). */
public final class Tasks {
	private record Job(long due, Runnable action) {
	}

	private static final List<Job> JOBS = new ArrayList<>();
	private static final List<Job> INCOMING = new ArrayList<>();
	private static long now;

	private Tasks() {
	}

	public static long now() {
		return now;
	}

	public static void later(int ticks, Runnable action) {
		INCOMING.add(new Job(now + Math.max(1, ticks), action));
	}

	/** Runs the action now and then every {@code interval} ticks, {@code times} times in total. */
	public static void repeat(int interval, int times, Runnable action) {
		for (int i = 0; i < times; i++) later(1 + i * interval, action);
	}

	public static void tick() {
		now++;
		JOBS.addAll(INCOMING);
		INCOMING.clear();
		Iterator<Job> it = JOBS.iterator();
		List<Job> due = new ArrayList<>();
		while (it.hasNext()) {
			Job job = it.next();
			if (job.due <= now) {
				due.add(job);
				it.remove();
			}
		}
		for (Job job : due) {
			try {
				job.action.run();
			} catch (Exception e) {
				com.omnispell.Omnispell.LOG.error("An Omnispell delayed effect failed", e);
			}
		}
	}

	/** Runs everything that is still waiting, right now. Used when the server stops so nothing is left behind. */
	public static void flush() {
		JOBS.addAll(INCOMING);
		INCOMING.clear();
		List<Job> all = new ArrayList<>(JOBS);
		JOBS.clear();
		for (Job job : all) {
			try {
				job.action.run();
			} catch (Exception ignored) {
			}
		}
	}
}
