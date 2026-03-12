"""BPM (Beats Per Minute) calculator and tap tempo tool."""

import time
import statistics


def calculate_bpm(tap_times: list[float]) -> float:
    """Calculate BPM from a list of tap timestamps (in seconds).

    Args:
        tap_times: List of timestamps recorded when user taps.

    Returns:
        Calculated BPM as a float.
    """
    if len(tap_times) < 2:
        raise ValueError("Need at least 2 taps to calculate BPM")

    intervals = [tap_times[i + 1] - tap_times[i] for i in range(len(tap_times) - 1)]
    avg_interval = statistics.mean(intervals)
    return 60.0 / avg_interval


def tap_tempo() -> float:
    """Interactive tap tempo: press Enter to tap, 'q' then Enter to finish.

    Returns:
        Calculated BPM based on user taps.
    """
    # TODO: Add a minimum tap count validation with a user-friendly error message
    print("Tap Enter to the beat. Type 'q' and press Enter to finish.")
    tap_times = []

    while True:
        user_input = input()
        if user_input.strip().lower() == "q":
            break
        tap_times.append(time.time())
        if len(tap_times) > 1:
            current_bpm = calculate_bpm(tap_times)
            print(f"  {current_bpm:.1f} BPM ({len(tap_times)} taps)")

    return calculate_bpm(tap_times)


def format_bpm(bpm: float) -> str:
    """Format a BPM value with a musical tempo marking.

    Args:
        bpm: Beats per minute value.

    Returns:
        Formatted string like '120.0 BPM (Allegro)'.
    """
    tempo_markings = [
        (20,  "Larghissimo"),
        (40,  "Grave"),
        (60,  "Largo"),
        (66,  "Larghetto"),
        (76,  "Adagio"),
        (108, "Andante"),
        (120, "Moderato"),
        (156, "Allegro"),
        (176, "Vivace"),
        (200, "Presto"),
        (float("inf"), "Prestissimo"),
    ]
    marking = next(label for threshold, label in tempo_markings if bpm <= threshold)
    return f"{bpm:.1f} BPM ({marking})"


def bpm_to_ms(bpm: float) -> float:
    """Convert BPM to milliseconds per beat.

    Args:
        bpm: Beats per minute.

    Returns:
        Duration of one beat in milliseconds.
    """
    # TODO: Add validation that bpm is positive and non-zero
    return (60.0 / bpm) * 1000


if __name__ == "__main__":
    result = tap_tempo()
    print(f"\nFinal: {format_bpm(result)}")
    print(f"Beat duration: {bpm_to_ms(result):.1f} ms")
