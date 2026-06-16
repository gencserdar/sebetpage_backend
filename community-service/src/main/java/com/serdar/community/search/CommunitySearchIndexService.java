package com.serdar.community.search;

import com.serdar.community.entity.Community;
import com.serdar.community.repository.CommunityRepository;
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
public class CommunitySearchIndexService {

    public static final String INDEX = "communities";

    private final OpenSearchClient client;
    private final CommunityRepository communities;

    @PostConstruct
    void bootstrap() throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(INDEX))).value();
        if (!exists) {
            client.indices().create(CreateIndexRequest.of(c -> c.index(INDEX).mappings(m -> m
                    .properties("id", p -> p.long_(l -> l))
                    .properties("isPrivate", p -> p.boolean_(b -> b))
                    .properties("name", p -> p.text(t -> t.fields("keyword", f -> f.keyword(k -> k))))
                    .properties("description", p -> p.text(t -> t)))));
        }
        reindexAll();
    }

    public void index(Community community) {
        try {
            client.index(i -> i.index(INDEX).id(String.valueOf(community.getId())).document(toDoc(community)));
        } catch (IOException e) {
            log.warn("Failed to index community {}: {}", community.getId(), e.getMessage());
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
            Map<String, Object> src = hit.source();
            if (src != null && Boolean.TRUE.equals(src.get("isPrivate"))) return;
            Object id = hit.source() == null ? hit.id() : hit.source().get("id");
            if (id instanceof Number n) ids.add(n.longValue());
            else if (id != null) ids.add(Long.parseLong(id.toString()));
        });
        return ids;
    }

    private static Query partialMatchQuery(String q) {
        String pattern = "*" + escapeWildcard(q) + "*";
        return Query.of(qb -> qb.bool(b -> b
                .minimumShouldMatch("1")
                .should(
                        Query.of(s -> s.wildcard(w -> w.field("name.keyword").value(pattern).caseInsensitive(true).boost(3f))),
                        Query.of(s -> s.prefix(p -> p.field("name.keyword").value(q).caseInsensitive(true).boost(3f))),
                        Query.of(s -> s.matchPhrasePrefix(m -> m.field("name").query(q).boost(2f))),
                        Query.of(s -> s.wildcard(w -> w.field("description").value(pattern).caseInsensitive(true))),
                        Query.of(s -> s.matchPhrasePrefix(m -> m.field("description").query(q)))
                )));
    }

    private static String escapeWildcard(String value) {
        return value.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?");
    }

    private void reindexAll() throws IOException {
        List<Community> all = communities.findAll();
        if (all.isEmpty()) return;
        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (Community c : all) {
            bulk.operations(BulkOperation.of(op -> op.index(IndexOperation.of(i -> i
                    .index(INDEX)
                    .id(String.valueOf(c.getId()))
                    .document(toDoc(c))))));
        }
        client.bulk(bulk.build());
        log.info("Indexed {} communities into OpenSearch", all.size());
    }

    private Map<String, Object> toDoc(Community c) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", c.getId());
        doc.put("name", c.getName());
        doc.put("description", c.getDescription());
        doc.put("isPrivate", Boolean.TRUE.equals(c.getIsPrivate()));
        return doc;
    }
}
