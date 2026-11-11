//! Bass Processing Module
//!
//! Provides Bass Enhancement with harmonic generation, Sub-bass generator,
//! and Bass Mono for low-frequency mono consolidation.

pub mod bass_enhancement;
pub mod sub_bass;
pub mod bass_mono;

pub use bass_enhancement::*;
pub use sub_bass::*;
pub use bass_mono::*;
