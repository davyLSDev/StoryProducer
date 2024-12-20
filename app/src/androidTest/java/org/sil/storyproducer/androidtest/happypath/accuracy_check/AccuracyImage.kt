package org.sil.storyproducer.androidtest.happypath.accuracy_check

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sil.storyproducer.androidtest.happypath.PhaseTestBase
import org.sil.storyproducer.androidtest.happypath.base.ImageBase
import org.sil.storyproducer.androidtest.happypath.base.annotation.ImageTest

@LargeTest
@ImageTest
@RunWith(AndroidJUnit4::class)
class AccuracyImage : ImageBase() {
    private var base: AccuracyPhaseBase = AccuracyPhaseBase(this)

    @Before
    fun setup() {
        PhaseTestBase.revertWorkspaceToCleanState(this)
        base.setUp()
    }

    @Test
    fun shouldBeAbleToSwipeBetweenSlides() {
        base.shouldBeAbleToSwipeBetweenSlides()
    }

    @Test
    fun shouldBeAbleToPlayRecordedAudioForSpecificSlide() {
        base.shouldBeAbleToPlayRecordedAudioForSpecificSlide()
    }

    @Test
    fun shouldBeAbleToToggleApprovedState() {
        base.shouldBeAbleToToggleApprovedState()
    }

    @Test
    fun approvalConfirmationPopupShouldBehaveCorrectly() {
        base.approvalConfirmationPopupShouldBehaveCorrectly()
    }
}