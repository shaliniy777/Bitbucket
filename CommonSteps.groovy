package com.experian.saas.event.publisher.steps

import com.experian.saas.event.publisher.helpers.Constants
import com.experian.saas.test.framework.api.ApiRequest
import com.experian.saas.test.framework.helpers.TestHelpers
import com.mashape.unirest.http.Unirest
import cucumber.api.groovy.EN
import cucumber.api.groovy.Hooks
import groovy.json.JsonSlurper
import io.cucumber.datatable.DataTable

this.metaClass.mixin(Hooks)
this.metaClass.mixin(EN)

Map<String, String> rqh = [:]

And(~/^I add the following headers to the REST request:$/) { DataTable data ->

    rqh = data.asMap(String.class, String.class)
}

And(~/^I send a post request to (.*) from (API service|HTTP client)$/) { String path, String from, String body ->

    if (from == "API service") {

        def requestBody = new JsonSlurper().parseText(body)

        apiRequest = new ApiRequest(
            path: path,
            headers: rqh,
            contentType: TestHelpers.EXPERIAN_JSON,
            body: requestBody
        )
        apiResponse = apiService.post(apiRequest)
    } else {
        Unirest.clearDefaultHeaders()
        rawResponse = Unirest.post("${Constants.BASE_URL}$path").headers(rqh).asJson()
    }

}

And(~/^I verify the (API service|HTTP client) response with:$/) { String from, DataTable data ->

    if (from == "API service") {
        assert apiResponse

        data.asMap(String.class, String.class).each { k, v ->
            assert apiResponse.body.get(k) == v
        }
    } else {
        assert rawResponse
        def raw = new JsonSlurper().parse(rawResponse.rawBody)
        data.asMap(String.class, String.class).each { k, v ->
            assert raw[k] == v
        }
    }
}
