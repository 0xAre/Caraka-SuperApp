package com.example.caraka.ui.screens.onboarding

import com.example.caraka.R

internal data class TourStep(val titleRes: Int, val descRes: Int)

internal val tourSteps = listOf(
    TourStep(R.string.tour_step1_title, R.string.tour_step1_desc),
    TourStep(R.string.tour_step2_title, R.string.tour_step2_desc),
    TourStep(R.string.tour_step3_title, R.string.tour_step3_desc),
    TourStep(R.string.tour_step4_title, R.string.tour_step4_desc),
    TourStep(R.string.tour_step5_title, R.string.tour_step5_desc)
)
