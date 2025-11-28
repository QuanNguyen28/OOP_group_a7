package app.model.service.ingest;

import app.model.domain.RawPost;
import java.util.stream.Stream;

public interface SocialConnector {
    String id();
    Stream<RawPost> fetch(QuerySpec spec);
}