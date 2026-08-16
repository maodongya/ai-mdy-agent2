package com.anvil.ui;

/** One row in the run execution trace table. */
record RunTraceRow(int seq, int step, String type, String summary, String metrics) {}
