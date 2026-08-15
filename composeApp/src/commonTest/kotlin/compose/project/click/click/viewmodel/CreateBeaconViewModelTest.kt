package compose.project.click.click.viewmodel // pragma: allowlist secret

import compose.project.click.click.ui.screens.BeaconDropCategory // pragma: allowlist secret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateBeaconViewModelTest {
    @Test
    fun stagedPhotoDoesNotResetCategory() {
        val vm = CreateBeaconViewModel()
        vm.setCategory(BeaconDropCategory.EVENT)
        vm.setTitle("Park hang")
        vm.setStagedPhoto(byteArrayOf(1, 2, 3), "image/jpeg")

        val state = vm.uiState.value
        assertEquals(BeaconDropCategory.EVENT, state.category)
        assertEquals("Park hang", state.title)
        assertTrue(state.hasStagedPhoto)
        assertEquals("image/jpeg", state.stagedPhotoMime)
        assertEquals(listOf<Byte>(1, 2, 3), state.stagedPhotoBytes?.toList())
    }

    @Test
    fun replaceAndRemovePhotoKeepDrafts() {
        val vm = CreateBeaconViewModel()
        vm.setCategory(BeaconDropCategory.HAZARD)
        vm.setTitle("Icy sidewalk")
        vm.setStagedPhoto(byteArrayOf(9), "image/jpeg")
        vm.setStagedPhoto(byteArrayOf(8, 8), "image/jpeg")
        assertEquals(
            listOf<Byte>(8, 8),
            vm.uiState.value.stagedPhotoBytes
                ?.toList(),
        )
        assertEquals(BeaconDropCategory.HAZARD, vm.uiState.value.category)

        vm.clearStagedPhoto()
        assertNull(vm.uiState.value.stagedPhotoBytes)
        assertEquals("Icy sidewalk", vm.uiState.value.title)
        assertEquals(BeaconDropCategory.HAZARD, vm.uiState.value.category)
    }

    @Test
    fun resetClearsFormForTheNextDrop() {
        val vm = CreateBeaconViewModel()
        vm.setCategory(BeaconDropCategory.STUDY)
        vm.setTitle("Library")
        vm.setStagedPhoto(byteArrayOf(1), "image/jpeg")
        vm.reset()
        val state = vm.uiState.value
        assertEquals(BeaconDropCategory.SOUNDTRACK, state.category)
        assertEquals("", state.title)
        assertNull(state.stagedPhotoBytes)
    }
}
