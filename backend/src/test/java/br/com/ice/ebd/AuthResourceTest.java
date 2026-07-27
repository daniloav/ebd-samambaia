package br.com.ice.ebd;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {

    @Test
    void loginComCredenciaisValidasRetornaToken() {
        given().contentType("application/json")
                .body("{\"username\":\"admin\",\"senha\":\"admin123\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("token", notNullValue())
                .body("ehAdmin", is(true));
    }

    @Test
    void loginComSenhaErradaRetorna401() {
        given().contentType("application/json")
                .body("{\"username\":\"admin\",\"senha\":\"errada\"}")
                .when().post("/api/auth/login")
                .then().statusCode(401);
    }

    @Test
    void rotaProtegidaSemTokenRetorna401() {
        given().when().get("/api/alunos").then().statusCode(401);
    }
}
