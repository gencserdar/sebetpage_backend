package com.serdar.user.search;

import com.serdar.user.entity.UserProfile;
import com.serdar.user.repository.UserProfileRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSearchIndexService {

    public static final String INDEX = "users";

    private final OpenSearchClient client;
    private final UserProfileRepository profiles;

    @PostConstruct
    void bootstrap() throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(INDEX))).value();
        if (!exists) {
            client.indices().create(CreateIndexRequest.of(c -> c.index(INDEX).mappings(m -> m
                    .properties("id", p -> p.long_(l -> l))
                    .properties("nickname", p -> p.text(t -> t.fields("keyword", f -> f.keyword(k -> k))))
                    .properties("name", p -> p.text(t -> t))
                    .properties("surname", p -> p.text(t -> t))
                    .properties("email", p -> p.keyword(k -> k)))));
        }
        reindexAll();
    }

    public void index(UserProfile profile) {
        try {
            client.index(i -> i.index(INDEX).id(String.valueOf(profile.getId())).document(toDoc(profile)));
        } catch (IOException e) {
            log.warn("Failed to index user {}: {}", profile.getId(), e.getMessage());
        }
    }

    public List<Long> searchIds(String keyword, int limit) throws IOException {
        if (keyword == null || keyword.isBlank()) return List.of();
        String q = keyword.trim();
        Query query = partialMatchQuery(q);
        SearchResponse<Map> response = client.search(SearchRequest.of(s -> s
                .index(INDEX)
                .size(Math.min(limit, 50))
                .query(query)), Map.class);
        List<Long> ids = new ArrayList<>();
        response.hits().hits().forEach(hit -> {
            Object id = hit.source() == null ? hit.id() : hit.source().get("id");
            if (id instanceof Number n) ids.add(n.longValue());
            else if (id != null) ids.add(Long.parseLong(id.toString()));
        });
        return ids;
    }

    /** Prefix + substring match so "te" finds "test", not only full tokens. */
    private static Query partialMatchQuery(String q) {
        String pattern = "*" + escapeWildcard(q) + "*";
        return Query.of(qb -> qb.bool(b -> b
                .minimumShouldMatch("1")
                .should(
                        Query.of(s -> s.wildcard(w -> w.field("nickname.keyword").value(pattern).caseInsensitive(true).boost(3f))),
                        Query.of(s -> s.prefix(p -> p.field("nickname.keyword").value(q).caseInsensitive(true).boost(3f))),
                        Query.of(s -> s.matchPhrasePrefix(m -> m.field("nickname").query(q).boost(2f))),
                        Query.of(s -> s.wildcard(w -> w.field("name").value(pattern).caseInsensitive(true).boost(2f))),
                        Query.of(s -> s.matchPhrasePrefix(m -> m.field("name").query(q).boost(2f))),
                        Query.of(s -> s.wildcard(w -> w.field("surname").value(pattern).caseInsensitive(true).boost(2f))),
                        Query.of(s -> s.matchPhrasePrefix(m -> m.field("surname").query(q).boost(2f)))
                )));
    }

    private static String escapeWildcard(String value) {
        return value.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?");
    }

    private void reindexAll() throws IOException {
        List<UserProfile> all = profiles.findAll();
        if (all.isEmpty()) return;
        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (UserProfile p : all) {
            bulk.operations(BulkOperation.of(op -> op.index(IndexOperation.of(i -> i
                    .index(INDEX)
                    .id(String.valueOf(p.getId()))
                    .document(toDoc(p))))));
        }
        client.bulk(bulk.build());
        log.info("Indexed {} users into OpenSearch", all.size());
    }

    private Map<String, Object> toDoc(UserProfile p) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", p.getId());
        doc.put("nickname", p.getNickname());
        doc.put("name", p.getName());
        doc.put("surname", p.getSurname());
        doc.put("email", p.getEmail());
        return doc;
    }
}
