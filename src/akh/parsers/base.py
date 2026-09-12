from __future__ import annotations

from abc import ABC, abstractmethod

from akh.models import ParsedSection, SourceDocument


class DocumentParser(ABC):
    @abstractmethod
    def supports(self, document: SourceDocument) -> bool:
        raise NotImplementedError

    @abstractmethod
    def parse(self, document: SourceDocument) -> list[ParsedSection]:
        raise NotImplementedError
