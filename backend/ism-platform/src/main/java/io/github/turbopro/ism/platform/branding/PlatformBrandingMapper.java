package io.github.turbopro.ism.platform.branding;

import org.apache.ibatis.annotations.*;

/** Global control-plane table: no tenant business data is read by this mapper. */
@Mapper
public interface PlatformBrandingMapper {
    @Select("SELECT system_name,logo_url,favicon_url,footer_text,version FROM plt_branding WHERE id=1")
    PlatformBrandingModels.Row get();

    @Select("SELECT system_name,logo_url,favicon_url,footer_text,version FROM plt_branding WHERE id=1 FOR UPDATE")
    PlatformBrandingModels.Row lock();

    @Update("UPDATE plt_branding SET system_name=#{name},logo_url=#{logo},favicon_url=#{icon},"
            + "footer_text=#{footer},version=version+1,updated_by=#{actor} WHERE id=1 AND version=#{version}")
    int save(String name, String logo, String icon, String footer, int version, long actor);
}
