/*
 * Copyright (C) 2017 Buglife, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.buglife.sdk;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.support.annotation.RequiresApi;

import com.buglife.sdk.reporting.BugReporter;
import com.buglife.sdk.reporting.SubmitReportLegacyService;
import com.buglife.sdk.reporting.SubmitReportService;

import org.json.JSONException;

import java.io.File;

class BugReporterImpl implements BugReporter {
    private final Context mContext;

    BugReporterImpl(Context context) {
        mContext = context;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Kick off submit report service in case there are cached reports still
            // available.
            SubmitReportLegacyService.start(mContext);
        }
    }

    @Override public void report(Report report) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            reportWithJobScheduler(report);
        } else {
            reportWithLegacy(report);
        }
    }

    private void reportWithLegacy(Report report) {
        try {
            String jsonReport = report.toJSON().toString();
            File file = FileUtils.getReportsCacheFile(mContext);
            FileUtils.appendLineToFile(jsonReport, file);
            SubmitReportLegacyService.start(mContext);
        } catch (JSONException e) {
            Log.e("Failed to encode report!", e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void reportWithJobScheduler(Report report) {
        JobScheduler jobScheduler = (JobScheduler) mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler == null) {
            throw new RuntimeException("Failed to obtain JobScheduler!");
        }

        try {
            int jobId = (int) System.currentTimeMillis();
            String jsonReport = report.toJSON().toString();

            PersistableBundle data = new PersistableBundle();
            data.putString(SubmitReportService.KEY_DATA_PAYLOAD, jsonReport);

            JobInfo info = new JobInfo.Builder(jobId, SubmitReportService.getComponentName(mContext))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setExtras(data)
                    .build();

            jobScheduler.schedule(info);
        } catch (JSONException e) {
            Log.e("Error serializing JSON report", e);
        }
    }
}
