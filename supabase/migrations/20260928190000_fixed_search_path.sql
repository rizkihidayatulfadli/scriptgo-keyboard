-- Security advisor (function_search_path_mutable): pin the search path of the two private helper
-- functions that had none. Neither refers to a table, so behaviour is unchanged.
alter function private.pricing_link() set search_path = '';
alter function private.product_info(text) set search_path = '';
