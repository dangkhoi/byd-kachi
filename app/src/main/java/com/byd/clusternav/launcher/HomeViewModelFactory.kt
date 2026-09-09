package com.byd.clusternav.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

/**
 * [ViewModelProvider.Factory] cho [HomeViewModel] — lấy phụ thuộc từ đồ thị DI ([com.byd.clusternav.AppContainer]).
 *
 * Thay cho `HomeViewModel.factory(context)` TẠM ở B5a: nay [HomeViewModel] được cấp qua một factory chuẩn AndroidX,
 * nhận [WorkspaceRepository] (do AppContainer sở hữu) + cờ [embedded] runtime. THUẦN (không chạm Context) → test JVM
 * off-device được: dựng factory với repository giả rồi `create(...)` ra HomeViewModel nối đúng repo + embedded.
 *
 * [KachiHomeActivity] kế thừa `android.app.Activity` (KHÔNG phải androidx `ComponentActivity`, vì :app không có
 * `androidx.activity` — thêm sẽ là phụ thuộc mới) nên KHÔNG dùng được `by viewModels()`; nó tự làm
 * [androidx.lifecycle.ViewModelStoreOwner] rồi lấy VM qua `ViewModelProvider(this, factory)`.
 */
class HomeViewModelFactory(
    private val repository: WorkspaceRepository,
    private val embedded: Boolean,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            "HomeViewModelFactory chỉ tạo HomeViewModel, không phải ${modelClass.name}"
        }
        return HomeViewModel(repository, embedded) as T
    }
}
