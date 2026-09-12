import unittest

from akh.citations import CitationManifest
from akh.chunking import SemanticChunker
from akh.models import ParsedSection, SourceDocument, SourceType


class CitationManifestTest(unittest.TestCase):
    def test_rejects_model_invented_citation(self) -> None:
        document = SourceDocument(
            "doc", "Design", SourceType.TEXT, "v1", None, b"unused", "text/plain"
        )
        chunks = SemanticChunker(max_words=20, overlap_words=3).chunk(
            document, [ParsedSection("Cloud Run hosts the query API")]
        )
        manifest = CitationManifest(chunks)
        valid, unknown = manifest.validate_answer("The API uses Cloud Run [C1], not GKE [C9].")
        self.assertFalse(valid)
        self.assertEqual(("C9",), unknown)


if __name__ == "__main__":
    unittest.main()
