package org.schabi.newpipe.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.grack.nanojson.JsonStringWriter;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.PeertubeHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class PeertubeInstanceListFragment extends Fragment {
    private static final int MENU_ITEM_RESTORE_ID = 123456;

    private final List<PeertubeInstance> instanceList = new ArrayList<>();
    private PeertubeInstance selectedInstance;
    private String savedInstanceListKey;
    private InstanceListAdapter instanceListAdapter;

    private ProgressBar progressBar;
    private SharedPreferences sharedPreferences;

    private CompositeDisposable disposables = new CompositeDisposable();

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        savedInstanceListKey = getString(R.string.peertube_instance_list_key);
        selectedInstance = PeertubeHelper.getCurrentInstance();
        updateInstanceList();

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_instance_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        initViews(rootView);
    }

    private void initViews(@NonNull final View rootView) {
        final TextView instanceHelpTV = rootView.findViewById(R.id.instanceHelpTV);
        instanceHelpTV.setText(getString(R.string.peertube_instance_url_help,
                getString(R.string.peertube_instance_list_url)));

        initButton(rootView);

        final RecyclerView listInstances = rootView.findViewById(R.id.instances);
        listInstances.setLayoutManager(new LinearLayoutManager(requireContext()));

        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(listInstances);

        instanceListAdapter = new InstanceListAdapter(requireContext(), itemTouchHelper);
        listInstances.setAdapter(instanceListAdapter);

        progressBar = rootView.findViewById(R.id.loading_progress_bar);
    }

    @Override
    public void onResume() {
        super.onResume();
        ThemeHelper.setTitleToAppCompatActivity(getActivity(),
                getString(R.string.peertube_instance_url_title));
    }

    @Override
    public void onPause() {
        super.onPause();
        saveChanges();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) {
            disposables.clear();
        }
        disposables = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        final MenuItem restoreItem = menu
                .add(Menu.NONE, MENU_ITEM_RESTORE_ID, Menu.NONE, R.string.restore_defaults);
        restoreItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        restoreItem.setIcon(AppCompatResources.getDrawable(requireContext(),
                R.drawable.ic_settings_backup_restore));
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == MENU_ITEM_RESTORE_ID) {
            restoreDefaults();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void updateInstanceList() {
        instanceList.clear();
        instanceList.addAll(PeertubeHelper.getInstanceList(requireContext()));
    }

    private void selectInstance(final PeertubeInstance instance) {
        selectedInstance = PeertubeHelper.selectInstance(instance, requireContext());
        sharedPreferences.edit().putBoolean(Constants.KEY_MAIN_PAGE_CHANGE, true).apply();
    }

    private void saveChanges() {
        final JsonStringWriter jsonWriter = JsonWriter.string().object().array("instances");
        for (final PeertubeInstance instance : instanceList) {
            jsonWriter.object();
            jsonWriter.value("name", instance.getName());
            jsonWriter.value("url", instance.getUrl());
            jsonWriter.end();
        }
        final String jsonToSave = jsonWriter.end().end().done();
        sharedPreferences.edit().putString(savedInstanceListKey, jsonToSave).apply();
    }

    private void restoreDefaults() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.restore_defaults)
                .setMessage(R.string.restore_defaults_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    sharedPreferences.edit().remove(savedInstanceListKey).apply();
                    selectInstance(PeertubeInstance.defaultInstance);
                    updateInstanceList();
                    instanceListAdapter.notifyDataSetChanged();
                })
                .show();
    }

    private void initButton(final View rootView) {
        final FloatingActionButton fab = rootView.findViewById(R.id.addInstanceButton);
        fab.setOnClickListener(v ->
                showAddItemDialog(requireContext()));
    }

    private void showAddItemDialog(final Context c) {
        final EditText urlET = new EditText(c);
        urlET.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlET.setHint(R.string.peertube_instance_add_help);
        final AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle(R.string.peertube_instance_add_title)
                .setIcon(R.drawable.place_holder_peertube)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.finish, (dialog1, which) -> {
                    final String url = urlET.getText().toString();
                    addInstance(url);
                })
                .create();
        dialog.setView(urlET, 50, 0, 50, 0);
        dialog.show();
    }

    private void addInstance(final String url) {
        final String cleanUrl = cleanUrl(url);
        if (cleanUrl == null) {
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        final Disposable disposable = Single.fromCallable(() -> {
            final PeertubeInstance instance = new PeertubeInstance(cleanUrl);
            instance.fetchInstanceMetaData();
            return instance;
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe((instance) -> {
                    progressBar.setVisibility(View.GONE);
                    add(instance);
                }, e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getActivity(), R.string.peertube_instance_add_fail,
                            Toast.LENGTH_SHORT).show();
                });
        disposables.add(disposable);
    }

    @Nullable
    private String cleanUrl(final String url) {
        String cleanUrl = url.trim();
        // if protocol not present, add https
        if (!cleanUrl.startsWith("http")) {
            cleanUrl = "https://" + cleanUrl;
        }
        // remove trailing slash
        cleanUrl = cleanUrl.replaceAll("/$", "");
        // only allow https
        if (!cleanUrl.startsWith("https://")) {
            Toast.makeText(getActivity(), R.string.peertube_instance_add_https_only,
                    Toast.LENGTH_SHORT).show();
            return null;
        }
        // only allow if not already exists
        for (final PeertubeInstance instance : instanceList) {
            if (instance.getUrl().equals(cleanUrl)) {
                Toast.makeText(getActivity(), R.string.peertube_instance_add_exists,
                        Toast.LENGTH_SHORT).show();
                return null;
            }
        }
        return cleanUrl;
    }

    private void add(final PeertubeInstance instance) {
        instanceList.add(instance);
        instanceListAdapter.notifyDataSetChanged();
    }

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public int interpolateOutOfBoundsScroll(final RecyclerView recyclerView,
                                                    final int viewSize,
                                                    final int viewSizeOutOfBounds,
                                                    final int totalSize,
                                                    final long msSinceStartScroll) {
                final int standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                        viewSizeOutOfBounds, totalSize, msSinceStartScroll);
                final int minimumAbsVelocity = Math.max(12,
                        Math.abs(standardSpeed));
                return minimumAbsVelocity * (int) Math.signum(viewSizeOutOfBounds);
            }

            @Override
            public boolean onMove(final RecyclerView recyclerView,
                                  final RecyclerView.ViewHolder source,
                                  final RecyclerView.ViewHolder target) {
                if (source.getItemViewType() != target.getItemViewType()
                        || instanceListAdapter == null) {
                    return false;
                }

                final int sourceIndex = source.getAdapterPosition();
                final int targetIndex = target.getAdapterPosition();
                instanceListAdapter.swapItems(sourceIndex, targetIndex);
                return true;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return true;
            }

            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int swipeDir) {
                final int position = viewHolder.getAdapterPosition();
                // do not allow swiping the selected instance
                if (instanceList.get(position).getUrl().equals(selectedInstance.getUrl())) {
                    instanceListAdapter.notifyItemChanged(position);
                    return;
                }
                instanceList.remove(position);
                instanceListAdapter.notifyItemRemoved(position);

                if (instanceList.isEmpty()) {
                    instanceList.add(selectedInstance);
                    instanceListAdapter.notifyItemInserted(0);
                }
            }
        };
    }

    /*//////////////////////////////////////////////////////////////////////////
    // List Handling
    //////////////////////////////////////////////////////////////////////////*/

    private class InstanceListAdapter
            extends RecyclerView.Adapter<InstanceListAdapter.TabViewHolder> {
        private final LayoutInflater inflater;
        private final ItemTouchHelper itemTouchHelper;
        private RadioButton lastChecked;

        InstanceListAdapter(final Context context, final ItemTouchHelper itemTouchHelper) {
            this.itemTouchHelper = itemTouchHelper;
            this.inflater = LayoutInflater.from(context);
        }

        public void swapItems(final int fromPosition, final int toPosition) {
            Collections.swap(instanceList, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);
        }

        @NonNull
        @Override
        public InstanceListAdapter.TabViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                                    final int viewType) {
            final View view = inflater.inflate(R.layout.item_instance, parent, false);
            return new InstanceListAdapter.TabViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final InstanceListAdapter.TabViewHolder holder,
                                     final int position) {
            holder.bind(position, holder);
        }

        @Override
        public int getItemCount() {
            return instanceList.size();
        }

        class TabViewHolder extends RecyclerView.ViewHolder {
            private final AppCompatImageView instanceIconView;
            private final TextView instanceNameView;
            private final TextView instanceUrlView;
            private final RadioButton instanceRB;
            private final ImageView handle;

            TabViewHolder(final View itemView) {
                super(itemView);

                instanceIconView = itemView.findViewById(R.id.instanceIcon);
                instanceNameView = itemView.findViewById(R.id.instanceName);
                instanceUrlView = itemView.findViewById(R.id.instanceUrl);
                instanceRB = itemView.findViewById(R.id.selectInstanceRB);
                handle = itemView.findViewById(R.id.handle);
            }

            @SuppressLint("ClickableViewAccessibility")
            void bind(final int position, final TabViewHolder holder) {
                handle.setOnTouchListener(getOnTouchListener(holder));

                final PeertubeInstance instance = instanceList.get(position);
                instanceNameView.setText(instance.getName());
                instanceUrlView.setText(instance.getUrl());
                instanceRB.setOnCheckedChangeListener(null);
                if (selectedInstance.getUrl().equals(instance.getUrl())) {
                    if (lastChecked != null && lastChecked != instanceRB) {
                        lastChecked.setChecked(false);
                    }
                    instanceRB.setChecked(true);
                    lastChecked = instanceRB;
                }
                instanceRB.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectInstance(instance);
                        if (lastChecked != null && lastChecked != instanceRB) {
                            lastChecked.setChecked(false);
                        }
                        lastChecked = instanceRB;
                    }
                });
                instanceIconView.setImageResource(R.drawable.place_holder_peertube);
            }

            @SuppressLint("ClickableViewAccessibility")
            private View.OnTouchListener getOnTouchListener(final RecyclerView.ViewHolder item) {
                return (view, motionEvent) -> {
                    if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (itemTouchHelper != null && getItemCount() > 1) {
                            itemTouchHelper.startDrag(item);
                            return true;
                        }
                    }
                    return false;
                };
            }
        }
    }
}
