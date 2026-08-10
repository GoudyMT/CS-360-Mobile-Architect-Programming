package com.goudy.inventoryapp.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.goudy.inventoryapp.data.InventoryRepository;
import com.goudy.inventoryapp.model.Part;

import java.util.List;

/**
 * Backs the inventory grid with a live, search-aware list: an empty search returns the whole
 * table, otherwise a DB-side LIKE match. It is reactive, so the grid updates itself when the
 * table changes rather than reloading on every return to the screen.
 */
public class InventoryViewModel extends AndroidViewModel {

    private final InventoryRepository repository;
    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final LiveData<List<Part>> parts;

    public InventoryViewModel(@NonNull Application application) {
        super(application);
        repository = new InventoryRepository(application);
        // Swap the source as the search text changes: empty -> the full table, otherwise a LIKE match.
        parts = Transformations.switchMap(query, text ->
                (text == null || text.trim().isEmpty())
                        ? repository.observeAll()
                        : repository.search(text.trim()));
    }

    public LiveData<List<Part>> getParts() {
        return parts;
    }

    public void setQuery(String text) {
        query.setValue(text);
    }
}
