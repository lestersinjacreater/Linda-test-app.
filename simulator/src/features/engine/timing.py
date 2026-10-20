"""Delays used by the engine and by the adoption odds, in one place so the two always agree (simulated seconds)."""
DETECT_LATENCY_S = (0.2, 1.5)  # SMS arrives -> Linda has scored it on the device
REPORT_DELAY_S = 0.2           # detection -> report sent to the gateway
WARN_LATENCY_S = (1.0, 7.0)    # the network acts -> the warning SMS lands on a phone
