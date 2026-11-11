//! Real-Time Safe Telemetry Collector (RMS, Peak, dBFS, Clipping, Processed Blocks)

use std::sync::atomic::{AtomicU32, AtomicU64, Ordering};
use crate::filters::biquad::AtomicF64;

pub struct TelemetryCollector {
    pub meter_rms_l: AtomicF64,
    pub meter_rms_r: AtomicF64,
    pub meter_peak_l: AtomicF64,
    pub meter_peak_r: AtomicF64,
    pub clipping_count: AtomicU32,
    pub processed_blocks: AtomicU64,
}

impl TelemetryCollector {
    pub fn new() -> Self {
        Self {
            meter_rms_l: AtomicF64::new(0.0),
            meter_rms_r: AtomicF64::new(0.0),
            meter_peak_l: AtomicF64::new(0.0),
            meter_peak_r: AtomicF64::new(0.0),
            clipping_count: AtomicU32::new(0),
            processed_blocks: AtomicU64::new(0),
        }
    }

    pub fn update(&self, rms_l: f32, rms_r: f32, peak_l: f32, peak_r: f32, clipped: bool) {
        self.meter_rms_l.store(rms_l as f64, Ordering::Relaxed);
        self.meter_rms_r.store(rms_r as f64, Ordering::Relaxed);
        self.meter_peak_l.store(peak_l as f64, Ordering::Relaxed);
        self.meter_peak_r.store(peak_r as f64, Ordering::Relaxed);

        if clipped {
            self.clipping_count.fetch_add(1, Ordering::Relaxed);
        }
        self.processed_blocks.fetch_add(1, Ordering::Relaxed);
    }

    pub fn read_snapshot(&self) -> (f32, f32, f32, f32, u32, u64) {
        (
            self.meter_rms_l.load(Ordering::Relaxed) as f32,
            self.meter_rms_r.load(Ordering::Relaxed) as f32,
            self.meter_peak_l.load(Ordering::Relaxed) as f32,
            self.meter_peak_r.load(Ordering::Relaxed) as f32,
            self.clipping_count.load(Ordering::Relaxed),
            self.processed_blocks.load(Ordering::Relaxed),
        )
    }
}
