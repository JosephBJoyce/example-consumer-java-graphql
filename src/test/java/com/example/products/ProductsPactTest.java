package com.example.products;

// --- JUnit + Pact test extensions ---
// This tells JUnit to run the test with Pact’s consumer test extension,
// which automatically spins up a mock provider based on our contract.
import org.junit.jupiter.api.extension.ExtendWith;
import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;

// --- GraphQL imports ---
// These are used by the GraphQL client/server wiring in the project.
// Not all are directly used in this test class, but they support the GraphQL setup.
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;

// --- Pact DSL imports ---
// These classes let us describe the expected JSON response body
// using flexible matchers instead of hard-coded values.
import au.com.dius.pact.consumer.dsl.PactDslJsonArray;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;

// --- Pact JUnit 5 integration ---
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

// --- Assertion helpers ---
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


// This connects the test class to Pact’s JUnit 5 support.
// Pact will manage the lifecycle of the mock provider server.
@ExtendWith(PactConsumerTestExt.class)

// This declares the logical name of the provider we are creating a contract with.
// This name is what will appear in PactFlow / Pact Broker.
@PactTestFor(providerName = "pactflow-example-provider-java-graphql")
public class ProductsPactTest {

  // This method DEFINES the contract interaction.
  // Think of this as: “What does the consumer expect from the provider?”
  // Pact records this into a pact file after the test runs.
  @Pact(consumer="pactflow-example-consumer-java-graphql")
  public RequestResponsePact getProduct(PactDslWithProvider builder) {

    // We define the expected JSON response structure using Pact’s DSL.
    // Important: we’re not hard-coding exact values — we’re defining TYPES.
    // That keeps the contract flexible but still safe.
    PactDslJsonBody body = new PactDslJsonBody();
    body
      .object("data")
        .object("product")
          .stringType("id", "10")          // must be a string
          .stringType("name", "product name")
          .stringType("type", "product series")
        .closeObject()
      .closeObject();

    // GraphQL requests are sent as JSON with a "query" field.
    // For contract tests, exact formatting matters — whitespace or structure
    // differences can cause mismatches if your real client formats it differently.
    // In production, teams often create helpers/builders to standardize this.
    final String query = """
      {
      "query": "{
        product(id: 10) {
          id
          name
          type
        }}
      "}
      """;

    // Here we build the full interaction definition:
    // State → Request → Expected Response
    return builder

      // Provider state:
      // This is a named setup condition.
      // During provider verification, the provider test will ensure this state exists.
      .given("a product with ID 10 exists")

      // Human-readable description of the interaction.
      .uponReceiving("a request to get a product via GraphQL")

        // What request we expect the consumer to send
        .path("/graphql")
        .headers(Map.of("content-type", "application/json"))
        .method("POST")
        .body(query)

      // What the mock provider should return
      .willRespondWith()
        .headers(Map.of("content-type", "application/json"))
        .status(200)
        .body(body)

      // Finalizes and returns the pact interaction definition
      .toPact();
  }

  // This is the actual test execution.
  // Pact spins up a mock HTTP server that behaves exactly
  // like the provider we just defined in the contract.
  @PactTestFor(pactMethod = "getProduct")
  @Test
  public void testGetProduct(MockServer mockServer) throws IOException, URISyntaxException {

    // We point our real consumer client at the Pact mock server URL.
    // This is the key idea:
    // we are testing our real client code
    // against a simulated provider defined by the contract
    Product product = new ProductClient()
        .setUrl(mockServer.getUrl())
        .getProduct("10");

    // This assertion proves the consumer correctly parsed the provider response.
    // If the request shape or response shape differs from the contract,
    // the mock server will fail the test.
    assertThat(product.getId(), is("10"));
  }
}
