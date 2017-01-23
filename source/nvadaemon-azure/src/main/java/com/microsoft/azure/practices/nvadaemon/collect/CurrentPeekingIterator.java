package com.microsoft.azure.practices.nvadaemon.collect;

import com.google.common.collect.PeekingIterator;

public interface CurrentPeekingIterator<T> extends PeekingIterator<T> {
    T current();
}
