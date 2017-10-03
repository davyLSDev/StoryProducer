package org.sil.storyproducer.controller.export;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v4.content.res.ResourcesCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.sil.storyproducer.R;
import org.sil.storyproducer.controller.phase.PhaseBaseActivity;
import org.sil.storyproducer.model.StoryState;
import org.sil.storyproducer.tools.StorySharedPreferences;
import org.sil.storyproducer.tools.file.TextFiles;
import org.sil.storyproducer.tools.media.story.AutoStoryMaker;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by annmcostantino on 10/1/2017.
 */

public class ShareActivity extends PhaseBaseActivity {
    private static final int FILE_CHOOSER_CODE = 1;
    private static final int LOCATION_MAX_CHAR_DISPLAY = 25;

    private static final int PROGRESS_MAX = 1000;

    private static final String PREF_FILE = "Export_Config";

    private static final String PREF_KEY_TITLE = "title";
    private static final String PREF_KEY_INCLUDE_BACKGROUND_MUSIC = "include_background_music";
    private static final String PREF_KEY_INCLUDE_PICTURES = "include_pictures";
    private static final String PREF_KEY_INCLUDE_TEXT = "include_text";
    private static final String PREF_KEY_INCLUDE_KBFX = "include_kbfx";
    private static final String PREF_KEY_FORMAT = "format";
    private static final String PREF_KEY_FILE = "file";

    private EditText mEditTextTitle;
    private View mLayoutConfiguration;
    private CheckBox mCheckboxSoundtrack;
    private CheckBox mCheckboxPictures;
    private CheckBox mCheckboxText;
    private CheckBox mCheckboxKBFX;
    private View mLayoutResolution;
    private ArrayAdapter<CharSequence> mResolutionAdapterAll;
    private ArrayAdapter<CharSequence> mResolutionAdapterHigh;
    private Spinner mSpinnerFormat;
    private EditText mEditTextLocation;
    private Button mButtonStart;
    private Button mButtonCancel;
    private ProgressBar mProgressBar;
    private TextView mShareHeader;
    private LinearLayout mShareSection;
    private TextView mNoVideosText;
    private ListView mVideosListView;

    private ExportedVideosAdapter videosAdapter;
    private String mStory;

    private String mOutputPath;

    private boolean mTextConfirmationChecked;

    //accordion variables
    private final int [] sectionIds = {R.id.export_section, R.id.share_section};
    private final int [] headerIds = {R.id.export_header, R.id.share_header};
    private View[] sectionViews = new View[sectionIds.length];
    private View[] headerViews = new View[headerIds.length];

    private Thread mProgressUpdater;
    private static final Object storyMakerLock = new Object();
    private static AutoStoryMaker storyMaker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mStory = StoryState.getStoryName();     //needs to be set first because some of the views use it
        boolean phaseUnlocked = StorySharedPreferences.isApproved(mStory, this);
        setContentView(R.layout.activity_create);
        mStory = StoryState.getStoryName();
        setupViews();
        invalidateOptionsMenu();
        if (phaseUnlocked) {
            findViewById(R.id.lock_overlay).setVisibility(View.INVISIBLE);
        } else {
            View mainLayout = findViewById(R.id.main_linear_layout);
            PhaseBaseActivity.disableViewAndChildren(mainLayout);
        }
        setVideoOrShareSectionOpen();
        loadPreferences();
        if (mEditTextTitle.getText().toString().equals(mStory)) {
            mEditTextTitle.setText(TextFiles.getDramatizationText(StoryState.getStoryName(), 0));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        toggleVisibleElements();

        watchProgress();
    }

    @Override
    protected void onPause() {
        mProgressUpdater.interrupt();

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        savePreferences();

        super.onDestroy();
    }

    /**
     * Listen for callback from FileChooserActivity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // See which child activity is calling us back.
        if (requestCode == FILE_CHOOSER_CODE) {
            if (resultCode == RESULT_OK) {
                setLocation(data.getStringExtra(FileChooserActivity.FILE_PATH));
            }
        }
    }

    /**
     * Remove focus from EditText when tapping outside. See http://stackoverflow.com/a/28939113/4639640
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if ( v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int)event.getRawX(), (int)event.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent( event );
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item =  menu.getItem(0);
        item.setIcon(R.drawable.ic_export);
        return true;
    }

    /**
     * Get handles to all necessary views and add some listeners.
     */
    private void setupViews() {

        //Initialize sectionViews[] with the integer id's of the various LinearLayouts
        //Add the listeners to the LinearLayouts's header section.
        for (int i = 0; i < sectionIds.length; i++) {
            sectionViews[i] = findViewById(sectionIds[i]);
            headerViews[i] = findViewById(headerIds[i]);
            setAccordionListener(findViewById(headerIds[i]), sectionViews[i]);
        }

        mEditTextTitle = (EditText) findViewById(R.id.editText_export_title);

        mLayoutConfiguration = findViewById(R.id.layout_export_configuration);

        mCheckboxSoundtrack = (CheckBox) findViewById(R.id.checkbox_export_soundtrack);
        mCheckboxPictures = (CheckBox) findViewById(R.id.checkbox_export_pictures);
        mCheckboxPictures.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean newState) {
                toggleVisibleElements();
            }
        });
        mCheckboxKBFX = (CheckBox) findViewById(R.id.checkbox_export_KBFX);
        mCheckboxKBFX.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean newState) {
                toggleVisibleElements();
            }
        });
        mCheckboxText = (CheckBox) findViewById(R.id.checkbox_export_text);
        mCheckboxText.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean newState) {
                toggleVisibleElements();
            }
        });

        String[] resolutionArray = getResources().getStringArray(R.array.export_resolution_options);
        List<String> immutableList = Arrays.asList(resolutionArray);
        ArrayList<String> resolutionList = new ArrayList<>(immutableList);

        mLayoutResolution = findViewById(R.id.layout_export_resolution);

        mResolutionAdapterHigh = new ArrayAdapter(this,
                R.layout.simple_spinner_dropdown_item, resolutionList);
        mResolutionAdapterHigh.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        mResolutionAdapterHigh.remove(mResolutionAdapterHigh.getItem(0));
        mResolutionAdapterHigh.remove(mResolutionAdapterHigh.getItem(0));
        mResolutionAdapterAll = ArrayAdapter.createFromResource(this,
                R.array.export_resolution_options, android.R.layout.simple_spinner_item);
        mResolutionAdapterAll.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);

        mSpinnerFormat = (Spinner) findViewById(R.id.spinner_export_format);
        ArrayAdapter<CharSequence> formatAdapter = ArrayAdapter.createFromResource(this,
                R.array.export_format_options, android.R.layout.simple_spinner_item);
        formatAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        mSpinnerFormat.setAdapter(formatAdapter);

        mEditTextLocation = (EditText) findViewById(R.id.editText_export_location);

        mButtonStart = (Button) findViewById(R.id.button_export_start);
        mButtonCancel = (Button) findViewById(R.id.button_export_cancel);

        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar_export);
        mProgressBar.setMax(PROGRESS_MAX);
        mProgressBar.setProgress(0);

        //share view
        mShareHeader = (TextView) findViewById(R.id.share_header);
        mShareSection = (LinearLayout) findViewById(R.id.share_section);
        videosAdapter = new ExportedVideosAdapter(this);
        mVideosListView = (ListView) findViewById(R.id.videos_list);
        mVideosListView.setAdapter(videosAdapter);
        mNoVideosText = (TextView)findViewById(R.id.no_videos_text);
        setVideoAdapterPaths();

    }

    /**
     * sets which one of the accordians starts open on the activity start
     */
    private void setVideoOrShareSectionOpen() {
        List<String> actualPaths = getExportedVideosForStory();
        if(actualPaths.size() > 0) {        //open the share view
            setSectionsClosedExceptView(findViewById(R.id.share_section));
        }
    }

    /**
     * This function sets the click listeners to implement the accordion functionality
     * for each section of the registration page
     *
     * @param headerView  a variable of type View denoting the field the user will click to open up
     *                    a section of the registration
     * @param sectionView a variable of type View denoting the section that will open up
     */
    private void setAccordionListener(final View headerView, final View sectionView) {
        headerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sectionView.getVisibility() == View.GONE) {
                    setSectionsClosedExceptView(sectionView);
                } else {
                    sectionView.setVisibility(View.GONE);
                    headerView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.gray, null));
                }
            }
        });
    }

    /**
     * sets all the accordion sections closed except for the one passed
     * @param sectionView that is wanted to be made open
     */
    private void setSectionsClosedExceptView(View sectionView) {
        for(int k = 0; k < sectionViews.length; k++) {
            if(sectionViews[k] == sectionView) {
                sectionViews[k].setVisibility(View.VISIBLE);
                headerViews[k].setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.primary, null));
            } else {
                sectionViews[k].setVisibility(View.GONE);
                headerViews[k].setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.gray, null));
            }
        }

    }

    /**
     * Ensure the proper elements are visible based on checkbox dependencies and whether export process is going.
     */
    private void toggleVisibleElements() {
        boolean isStoryMakerBusy = false;

        int visibilityPreExport = View.VISIBLE;
        int visibilityWhileExport = View.GONE;
        synchronized (storyMakerLock) {
            if (storyMaker != null) {
                isStoryMakerBusy = true;

                visibilityPreExport = View.GONE;
                visibilityWhileExport = View.VISIBLE;
            }
        }

        mLayoutConfiguration.setVisibility(visibilityPreExport);
        mButtonStart.setVisibility(visibilityPreExport);
        mButtonCancel.setVisibility(visibilityWhileExport);
        mProgressBar.setVisibility(visibilityWhileExport);

        mCheckboxKBFX.setVisibility(mCheckboxPictures.isChecked() ? View.VISIBLE : View.GONE);


        mLayoutResolution.setVisibility(mCheckboxPictures.isChecked() || mCheckboxText.isChecked()
                ? View.VISIBLE : View.GONE);

        mShareHeader.setVisibility(visibilityPreExport);
        if(isStoryMakerBusy) {
            mShareSection.setVisibility(View.GONE);
        }

        if (mCheckboxText.isChecked()) {
            if (mTextConfirmationChecked) {
            } else {
            }
        } else {
            mTextConfirmationChecked = true;
        }

    }
    /*
     * Set the path for export location, including UI.
     * @param path new export location.
     */
    private void setLocation(String path) {
        if(path == null) {
            path = "";
        }
        mOutputPath = path;
        String display = path;
        if(path.length() > LOCATION_MAX_CHAR_DISPLAY) {
            display = "..." + path.substring(path.length() - LOCATION_MAX_CHAR_DISPLAY + 3);
        }
        mEditTextLocation.setText(display);
    }


    /**
     * sets the videos for the list adapter
     */
    private void setVideoAdapterPaths() {
        List<String> actualPaths = getExportedVideosForStory();
        if(actualPaths.size() > 0) {
            mNoVideosText.setVisibility(View.GONE);
        }
        videosAdapter.setVideoPaths(actualPaths);
    }

    /**
     * Returns the the video paths that are saved in preferences and then checks to see that they actually are files that exist
     * @return Array list of video paths
     */
    private List<String> getExportedVideosForStory() {
        List<String> actualPaths = new ArrayList<>();
        List<String> videoPaths = StorySharedPreferences.getExportedVideosForStory(mStory);
        for(String path : videoPaths) {          //make sure the file actually exists
            File file = new File(path);
            if(file.exists() && !actualPaths.contains(path)) {
                actualPaths.add(path);
            }
            else {
                //If the file doesn't exist or we encountered it a second time in the list, remove it.
                StorySharedPreferences.removeExportedVideoForStory(path, mStory);
            }
        }
        return actualPaths;
    }

    /**
     * Save current configuration options to shared preferences.
     */
    private void savePreferences() {
        SharedPreferences.Editor editor = getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit();

        editor.putBoolean(PREF_KEY_INCLUDE_BACKGROUND_MUSIC, mCheckboxSoundtrack.isChecked());
        editor.putBoolean(PREF_KEY_INCLUDE_PICTURES, mCheckboxPictures.isChecked());
        editor.putBoolean(PREF_KEY_INCLUDE_TEXT, mCheckboxText.isChecked());
        editor.putBoolean(PREF_KEY_INCLUDE_KBFX, mCheckboxKBFX.isChecked());

        editor.putString(PREF_KEY_FORMAT, mSpinnerFormat.getSelectedItem().toString());

        editor.putString(mStory + PREF_KEY_TITLE, mEditTextTitle.getText().toString());
        editor.putString(mStory + PREF_KEY_FILE, mOutputPath);

        editor.apply();
    }

    /**
     * Load configuration options from shared preferences.
     */
    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);

        mCheckboxSoundtrack.setChecked(prefs.getBoolean(PREF_KEY_INCLUDE_BACKGROUND_MUSIC, true));
        mCheckboxPictures.setChecked(prefs.getBoolean(PREF_KEY_INCLUDE_PICTURES, true));
        mCheckboxText.setChecked(prefs.getBoolean(PREF_KEY_INCLUDE_TEXT, false));
        mCheckboxKBFX.setChecked(prefs.getBoolean(PREF_KEY_INCLUDE_KBFX, true));

        setSpinnerValue(mSpinnerFormat, prefs.getString(PREF_KEY_FORMAT, null));

        mEditTextTitle.setText(prefs.getString(mStory + PREF_KEY_TITLE, mStory));
        setLocation(prefs.getString(mStory + PREF_KEY_FILE, null));

        //mTextConfirmationChecked = true;
    }

    /**
     * Attempt to set the value of the spinner to the given string value based on options available.
     * @param spinner spinner to update value.
     * @param value new value of spinner.
     */
    private void setSpinnerValue(Spinner spinner, String value) {
        if(value == null) {
            return;
        }

        for(int i = 0; i < spinner.getCount(); i++) {
            if(value.equals(spinner.getItemAtPosition(i).toString())) {
                spinner.setSelection(i);
            }
        }
    }


    private void stopExport() {
        synchronized (storyMakerLock) {
            if (storyMaker != null) {
                storyMaker.close();
                storyMaker = null;
            }
        }
        //update the list view
        setVideoAdapterPaths();
        toggleVisibleElements();
    }

    private void watchProgress() {
        mProgressUpdater = new Thread(PROGRESS_UPDATER);
        mProgressUpdater.start();
        toggleVisibleElements();
    }

    private void updateProgress(final int progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressBar.setProgress(progress);
            }
        });
    }

    private final Runnable PROGRESS_UPDATER = new Runnable() {
        @Override
        public void run() {
            boolean isDone = false;
            while(!isDone) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    //If progress updater is interrupted, just stop.
                    return;
                }
                double progress;
                synchronized (storyMakerLock) {
                    //Stop if storyMaker was cancelled by someone else.
                    if(storyMaker == null) {
                        updateProgress(0);
                        return;
                    }

                    progress = storyMaker.getProgress();
                    isDone = storyMaker.isDone();
                }
                updateProgress((int) (progress * PROGRESS_MAX));
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //save the file only when the video file is actually created
                    String ext = getFormatExtension();
                    File output = new File(mOutputPath + ext);
                    StorySharedPreferences.addExportedVideoForStory(output.getAbsolutePath(), mStory);
                    stopExport();
                    Toast.makeText(getBaseContext(), "Video created!", Toast.LENGTH_LONG).show();
                    setSectionsClosedExceptView(findViewById(R.id.share_section));

                }
            });
        }
    };

    private String getFormatExtension() {
        String ext = ".mp4";
        String[] formats = getResources().getStringArray(R.array.export_format_options);
        String selectedFormat = mSpinnerFormat.getSelectedItem().toString();

        for (int i = 0; i < formats.length; ++i) {
            if (selectedFormat.equals(formats[i])) {
                ext = getResources().getStringArray(R.array.export_format_extensions)[i];
                break;
            }
        }

        return ext;
    }

}