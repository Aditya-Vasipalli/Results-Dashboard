package edu.vit.results;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Drives the real HTTP API against the vit_results_test Postgres database. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:postgresql://localhost:5432/vit_results_test",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.reset=true",
        })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    @Value("${local.server.port}")
    int port;

    static final HttpClient http = HttpClient.newHttpClient();
    static final JsonMapper json = JsonMapper.builder().build();

    record Res(int status, JsonNode body) {}

    Res call(String method, String path, String token, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode node = r.body().isBlank() ? null : json.readTree(r.body());
        return new Res(r.statusCode(), node);
    }

    String login(String email, String password) throws Exception {
        Res r = call("POST", "/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
        assertEquals(200, r.status(), "login " + email);
        return r.body().get("token").asString();
    }

    String admin() throws Exception { return login("admin@vit.edu", "admin123"); }
    String iyer() throws Exception { return login("iyer@vit.edu", "teacher123"); }

    long subjectId(String token, String code) throws Exception {
        for (JsonNode s : call("GET", "/api/subjects", token, null).body()) {
            if (s.get("code").asString().equals(code)) return s.get("id").asLong();
        }
        throw new AssertionError("no subject " + code);
    }

    JsonNode row(JsonNode gradebook, String prn) {
        for (JsonNode r : gradebook.get("rows")) if (r.get("prn").asString().equals(prn)) return r;
        throw new AssertionError("no row " + prn);
    }

    @Test @Order(1)
    void loginChecksCredentials() throws Exception {
        assertEquals(401, call("POST", "/api/auth/login", null, "{\"email\":\"admin@vit.edu\",\"password\":\"wrong\"}").status());
        assertEquals(401, call("POST", "/api/auth/login", null, "{\"email\":\"ghost@vit.edu\",\"password\":\"x\"}").status());
        assertEquals(400, call("POST", "/api/auth/login", null, "{\"email\":\"\",\"password\":\"\"}").status());
        assertNotNull(login("ADMIN@vit.edu", "admin123"), "email is case-insensitive");
        Res me = call("GET", "/api/auth/me", admin(), null);
        assertEquals("ADMIN", me.body().get("user").get("role").asString());
    }

    @Test @Order(2)
    void rolesAreEnforced() throws Exception {
        String student = login("aarav.mehta@vit.edu", "student123");
        String teacher = iyer();
        assertEquals(401, call("GET", "/api/student/result", null, null).status());
        assertEquals(401, call("GET", "/api/student/result", "garbage.token.here", null).status());
        assertEquals(403, call("GET", "/api/admin/stats", student, null).status());
        assertEquals(403, call("GET", "/api/subjects", student, null).status());
        assertEquals(403, call("GET", "/api/admin/users", teacher, null).status());
        assertEquals(403, call("GET", "/api/student/result", teacher, null).status());
        Res missing = call("GET", "/api/does-not-exist", admin(), null);
        assertEquals(404, missing.status());
        assertEquals("Not found", missing.body().get("error").asString());
    }

    @Test @Order(3)
    void studentSeesTheirPublishedSemesterResult() throws Exception {
        Res r = call("GET", "/api/student/result", login("aarav.mehta@vit.edu", "student123"), null);
        assertEquals(200, r.status());
        assertTrue(r.body().get("published").asBoolean());
        JsonNode result = r.body().get("result");
        assertEquals("12310001", result.get("student").get("prn").asString());
        assertEquals(4, result.get("subjects").size());
        assertEquals(14, result.get("totalCredits").asInt());
        for (JsonNode s : result.get("subjects")) {
            double expected = Math.round((s.get("mse").asDouble() * 30 + s.get("ese").asDouble() * 70)) / 100.0;
            assertEquals(expected, s.get("total").asDouble(), 0.001);
        }
        assertFalse(result.get("sgpa").isNull());
    }

    @Test @Order(4)
    void teacherEntersMarksOnlyForOwnSubjectWithValidation() throws Exception {
        String t = iyer();
        String other = login("kapoor@vit.edu", "teacher123");
        Res mine = call("GET", "/api/subjects", t, null);
        assertEquals(1, mine.body().size());
        long cn = subjectId(t, "CS3201");

        assertEquals(403, call("GET", "/api/subjects/" + cn + "/marks", other, null).status());
        assertEquals(403, call("PUT", "/api/subjects/" + cn + "/marks", other, "{\"entries\":[]}").status());
        assertEquals(404, call("GET", "/api/subjects/999999/marks", t, null).status());

        JsonNode book = call("GET", "/api/subjects/" + cn + "/marks", t, null).body();
        assertTrue(book.get("canEdit").asBoolean());
        assertEquals(12, book.get("rows").size());
        long nisha = row(book, "12310010").get("studentId").asLong();
        String put = "/api/subjects/" + cn + "/marks";

        assertEquals(400, call("PUT", put, t, "{\"entries\":[{\"studentId\":" + nisha + ",\"mse\":101,\"ese\":50}]}").status());
        assertEquals(400, call("PUT", put, t, "{\"entries\":[{\"studentId\":" + nisha + ",\"mse\":50,\"ese\":-1}]}").status());
        assertEquals(400, call("PUT", put, t, "{\"entries\":[{\"studentId\":" + nisha + ",\"mse\":\"abc\",\"ese\":50}]}").status());
        assertEquals(400, call("PUT", put, t, "{\"entries\":[{\"studentId\":999999,\"mse\":50,\"ese\":50}]}").status());
        assertEquals(400, call("PUT", put, t, "{\"entries\":[{\"studentId\":" + nisha + ",\"mse\":1,\"ese\":1},{\"studentId\":" + nisha + ",\"mse\":2,\"ese\":2}]}").status());

        Res ok = call("PUT", put, t, "{\"entries\":[{\"studentId\":" + nisha + ",\"mse\":80,\"ese\":90}]}");
        assertEquals(200, ok.status());
        assertEquals(1, ok.body().get("saved").asInt());
        JsonNode r = row(call("GET", "/api/subjects/" + cn + "/marks", t, null).body(), "12310010");
        assertEquals(87.0, r.get("total").asDouble());
        assertEquals("A+", r.get("grade").asString());
        assertEquals("PASS", r.get("status").asString());

        call("PUT", put, t, "{\"entries\":[{\"studentId\":" + nisha + ",\"mse\":90,\"ese\":35}]}");
        r = row(call("GET", "/api/subjects/" + cn + "/marks", t, null).body(), "12310010");
        assertEquals("F", r.get("grade").asString(), "ESE below 40 fails the subject");

        Res cleared = call("PUT", put, t, "{\"entries\":[{\"studentId\":" + nisha + ",\"mse\":null,\"ese\":null}]}");
        assertEquals(1, cleared.body().get("cleared").asInt());
        JsonNode sem = call("GET", "/api/student/result", login("nisha.verma@vit.edu", "student123"), null).body().get("result");
        assertEquals("PENDING", sem.get("status").asString());
        assertTrue(sem.get("sgpa").isNull());

        // Admin can view the gradebook but not edit it.
        JsonNode adminView = call("GET", "/api/subjects/" + cn + "/marks", admin(), null).body();
        assertFalse(adminView.get("canEdit").asBoolean());
        assertEquals(403, call("PUT", put, admin(), "{\"entries\":[]}").status());
    }

    @Test @Order(5)
    void unpublishedResultsAreHiddenFromStudents() throws Exception {
        String a = admin();
        String s = login("diya.nair@vit.edu", "student123");
        assertEquals(200, call("PUT", "/api/admin/publication", a, "{\"published\":false}").status());
        JsonNode hidden = call("GET", "/api/student/result", s, null).body();
        assertFalse(hidden.get("published").asBoolean());
        assertTrue(hidden.get("result").isNull());
        assertEquals(200, call("PUT", "/api/admin/publication", a, "{\"published\":true}").status());
        assertTrue(call("GET", "/api/student/result", s, null).body().get("published").asBoolean());
    }

    @Test @Order(6)
    void adminManagesUsers() throws Exception {
        String a = admin();
        String base = "{\"name\":\"New Student\",\"email\":\"new.student@vit.edu\",\"password\":\"secret1\",\"role\":\"STUDENT\"";
        assertEquals(400, call("POST", "/api/admin/users", a, base + "}").status(), "student needs a PRN");
        assertEquals(400, call("POST", "/api/admin/users", a, base + ",\"prn\":\"12!\"}").status());
        assertEquals(409, call("POST", "/api/admin/users", a, base + ",\"prn\":\"12310001\"}").status(), "duplicate PRN");
        Res created = call("POST", "/api/admin/users", a, base + ",\"prn\":\"12310099\"}");
        assertEquals(201, created.status());
        assertEquals(409, call("POST", "/api/admin/users", a, base.replace("new.student", "NEW.STUDENT") + ",\"prn\":\"12310098\"}").status(), "duplicate email");
        assertEquals(400, call("POST", "/api/admin/users", a, "{\"name\":\"X\",\"email\":\"nope\",\"password\":\"secret1\",\"role\":\"TEACHER\"}").status());

        long myId = call("GET", "/api/auth/me", a, null).body().get("user").get("id").asLong();
        assertEquals(400, call("DELETE", "/api/admin/users/" + myId, a, null).status());
        assertEquals(400, call("PUT", "/api/admin/users/" + myId, a,
                "{\"name\":\"Dr. Ananya Rao\",\"email\":\"admin@vit.edu\",\"role\":\"TEACHER\"}").status());

        // A deleted user's token stops working immediately.
        String newbie = login("new.student@vit.edu", "secret1");
        assertEquals(200, call("GET", "/api/student/result", newbie, null).status());
        assertEquals(200, call("DELETE", "/api/admin/users/" + created.body().get("id").asLong(), a, null).status());
        assertEquals(401, call("GET", "/api/student/result", newbie, null).status());

        JsonNode stats = call("GET", "/api/admin/stats", a, null).body();
        assertEquals(12, stats.get("students").asInt());
        assertEquals(4, stats.get("subjects").asInt());
        assertEquals(4, stats.get("bySubject").size());
        assertTrue(call("GET", "/api/admin/audit?limit=5", a, null).body().size() > 0);
        assertEquals(12, call("GET", "/api/admin/results", a, null).body().size());
    }

    @Test @Order(7)
    void adminEditsSubjects() throws Exception {
        String a = admin();
        long os = subjectId(a, "CS3203");
        long toc = subjectId(a, "CS3204");
        String path = "/api/admin/subjects/" + os;
        assertEquals(409, call("PUT", path, a, "{\"code\":\"cs3204\",\"name\":\"OS\",\"credits\":3}").status());
        assertEquals(400, call("PUT", path, a, "{\"code\":\"CS3203\",\"name\":\"OS\",\"credits\":0}").status());
        assertEquals(400, call("PUT", path, a, "{\"code\":\"CS3203\",\"name\":\"OS\",\"credits\":3,\"teacherId\":999999}").status());
        Res ok = call("PUT", path, a, "{\"code\":\"CS3203\",\"name\":\"Operating Systems\",\"credits\":4,\"teacherId\":null}");
        assertEquals(200, ok.status());
        assertTrue(ok.body().get("teacherId").isNull());
        assertEquals(403, call("GET", "/api/subjects/" + os + "/marks", login("fernandes@vit.edu", "teacher123"), null).status(),
                "unassigned teacher loses access");
        assertNotEquals(os, toc);
    }

    @Test @Order(8)
    void changePassword() throws Exception {
        String t = login("kulkarni@vit.edu", "teacher123");
        assertEquals(400, call("POST", "/api/auth/change-password", t, "{\"current\":\"wrong\",\"next\":\"newpass1\"}").status());
        assertEquals(400, call("POST", "/api/auth/change-password", t, "{\"current\":\"teacher123\",\"next\":\"123\"}").status());
        assertEquals(200, call("POST", "/api/auth/change-password", t, "{\"current\":\"teacher123\",\"next\":\"newpass1\"}").status());
        assertNotNull(login("kulkarni@vit.edu", "newpass1"));
    }
}
