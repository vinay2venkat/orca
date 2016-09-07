/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.applications.tasks

import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.tasks.AbstractFront50Task
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
class DeleteApplicationTask extends AbstractFront50Task {
  @Override
  Map<String, Object> performRequest(String account, Application application) {
    Map<String, Object> outputs = [:]

    boolean deletePermission

    front50Service.credentials.findAll { it.global }.collect {
      it.name
    }.each { String globalAccountName ->
      try {
        def existingGlobalApplication = front50Service.get(globalAccountName, application.name)
        if (existingGlobalApplication) {
          outputs.previousState = existingGlobalApplication

          existingGlobalApplication.updateAccounts(existingGlobalApplication.listAccounts() - account)
          if (existingGlobalApplication.listAccounts()) {
            // application still exists in at least one other account, do not delete.
            front50Service.update(globalAccountName, existingGlobalApplication)
          } else {
            // application is not associated with any accounts, delete.
            front50Service.delete(globalAccountName, application.name)
            deletePermission = true
          }
        }
      } catch (RetrofitError e) {
        if (e.response.status == 404) {
          return
        }
        throw e
      }
    }

    if (deletePermission) {
      try {
        front50Service.deletePermission(application.name)
      } catch (RetrofitError re) {
        if (re.response.status == 404) {
          return
        }
        throw re
      }
    }

    outputs
  }

  @Override
  String getNotificationType() {
    return "deleteapplication"
  }
}
